package com.faforever.server.game;

import com.faforever.server.game.dto.FAFMap;
import com.faforever.server.game.dto.GameDetails;
import com.faforever.server.game.dto.GamePlayer;
import com.faforever.server.game.dto.LaunchDetails;
import com.faforever.server.game.dto.RatingRange;
import com.faforever.server.game.dto.Visibility;
import com.faforever.server.interceptor.cdi.MDCInterceptor;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.client.GPGMessage;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.client.dto.GameStatus;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.client.dto.JsonStatsPayload;
import com.faforever.server.message.client.dto.LobbyMode;
import com.faforever.server.message.client.dto.ResultDetails;
import com.faforever.server.message.service.InboundGPGMessage;
import com.faforever.server.message.service.OutboundGPGMessage;
import com.faforever.server.message.service.OutboundGameMessage;
import com.faforever.server.player.GameInfoWithFilter;
import com.faforever.server.player.PlayerGameFilter;
import com.faforever.server.player.PlayerTracker;
import com.faforever.server.player.dto.LeaderboardRating;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.Dependent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

@Slf4j
@Dependent
class GameHandler {

    private final GameTracker gameTracker;
    private final PlayerTracker playerTracker;

    private final Emitter<OutboundGPGMessage<GPGMessage.Server>> gpgEmitter;
    private final Emitter<OutboundGameMessage<GameMessage.Server>> lobbyEmitter;
    private final ThreadPoolExecutor gpgMessageExecutor;

    private final ScheduledExecutorService scheduledExecutorService;

    private final GameSettings gameSettings;

    private final Counter gpgDroppedMessageCounter;

    private final Map<Integer, PlayerDetails> playerIdMap = new ConcurrentHashMap<>();

    private final LongAdder desyncs = new LongAdder();
    private final GameHandlerRepository gameHandlerRepository;

    @Getter(AccessLevel.PACKAGE)
    private @Nullable Integer gameId;
    private @Nullable GameDetails details;
    private @Nullable Instant hostedAt;
    private @Nullable Instant launchedAt;

    private State state = State.INITIALIZING;
    private PlayerGameFilter playerGameFilter = PlayerGameFilter.VISIBLE_TO_NONE;

    GameHandler(GameTracker gameTracker, PlayerTracker playerTracker, MeterRegistry meterRegistry,
                @Channel("lobby-outbound") Emitter<OutboundGPGMessage<GPGMessage.Server>> gpgEmitter,
                @Channel("lobby-outbound") Emitter<OutboundGameMessage<GameMessage.Server>> lobbyEmitter,
                @ConfigProperty(name = "faf.max-message-queue-size", defaultValue = "0") int maxMessageQueueSize,
                ScheduledExecutorService scheduledExecutorService,
                GameSettings gameSettings, GameHandlerRepository gameHandlerRepository) {
        this.gameTracker = gameTracker;
        this.playerTracker = playerTracker;
        this.gpgEmitter = gpgEmitter;
        this.lobbyEmitter = lobbyEmitter;
        this.gpgMessageExecutor = createMessageExecutor(
                maxMessageQueueSize == 0 ? Integer.MAX_VALUE : maxMessageQueueSize);
        this.scheduledExecutorService = scheduledExecutorService;
        this.gameSettings = gameSettings;
        this.gameHandlerRepository = gameHandlerRepository;
        gpgDroppedMessageCounter = meterRegistry.counter("faf.gpg.messages.dropped");
    }

    private ThreadPoolExecutor createMessageExecutor(int queueSize) {
        ThreadFactory threadFactory = Thread.ofVirtual()
                                            .name("gpg-" + gameId + "-", 0)
                                            .uncaughtExceptionHandler((_, _) -> {})
                                            .factory();
        return new ThreadPoolExecutor(0, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(queueSize), threadFactory,
                                      (_, executor) -> {
                                          MDC.put(MDCInterceptor.GAME_ID, String.valueOf(gameId));
                                          if (executor.isShutdown()) {
                                              LOG.info("Dropping gpg message queued after game closed");
                                          } else {
                                              LOG.warn("Unable to queue gpg message due to full capacity");
                                              gpgDroppedMessageCounter.increment();
                                          }
                                          MDC.remove(MDCInterceptor.GAME_ID);
                                      }) {

            @Override
            protected void afterExecute(Runnable r, @Nullable Throwable t) {
                super.afterExecute(r, t);
                if (t != null) {
                    LOG.warn("Unable to process gpg message", t);
                }
            }
        };
    }

    @Nullable GameInfoWithFilter createGameInfo() {
        if (details == null || gameId == null) {
            return null;
        }

        GameStatus gameStatus = switch (state) {
            case LOBBY -> GameStatus.OPEN;
            case LIVE -> GameStatus.PLAYING;
            case INITIALIZING, ENDED -> GameStatus.CLOSED;
        };
        Map<Integer, Set<Integer>> teamPlayerMap = gameSettings.getTeamPlayerMap();
        Set<GameInfo.Team> teamIds = new HashSet<>();
        Map<Integer, Set<String>> teams = new HashMap<>();

        teamPlayerMap.forEach((teamId, playerIds) -> {
            Set<String> teamPlayerNames = new HashSet<>(playerIds.size());
            Set<Integer> teamPlayerIds = new HashSet<>(playerIds.size());
            playerIds.stream()
                     .map(playerIdMap::get)
                     .filter(Objects::nonNull)
                     .filter(playerDetails -> playerDetails.getState() == PlayerGameState.CONNECTED)
                     .forEach(playerDetails -> {
                         teamPlayerNames.add(playerDetails.getName());
                         teamPlayerIds.add(playerDetails.getId());
                     });
            teams.put(teamId, teamPlayerNames);
            teamIds.add(new GameInfo.Team(teamId, teamPlayerIds));
        });

        int numActivePlayers = (int) teamIds.stream()
                                            .filter(team -> team.teamId() > 0)
                                            .map(GameInfo.Team::playerIds)
                                            .mapToLong(Collection::size)
                                            .sum();

        RatingRange ratingRange = details.ratingRange();
        GameVisibility visibility = switch (details.visibility()) {
            case Visibility.Private _ -> GameVisibility.PRIVATE;
            case Visibility.Public _ -> GameVisibility.PUBLIC;
        };
        FAFMap map = gameSettings.getMap();
        GameInfo gameInfo = new GameInfo(gameId, gameSettings.getGameTitle(), details.host().name(), details.gameType(),
                                         gameSettings.getNumSlots(), numActivePlayers, visibility,
                                         details.password() != null,
                                         gameStatus, details.featuredMod(), details.leaderboard(),
                                         gameSettings.getSimMods(),
                                         map.mapName(), hostedAt,
                                         launchedAt == null ? null : launchedAt.getEpochSecond(), details.leaderboard(),
                                         ratingRange.min(), ratingRange.max(), ratingRange.enforce(), teams, teamIds);
        return new GameInfoWithFilter(gameInfo, playerGameFilter);
    }

    void initializeDetails(int gameId, String title, String mapName, GameDetails gameDetails) {
        if (details != null || this.gameId != null) {
            throw new IllegalStateException("Game details already set");
        }

        this.gameId = gameId;
        this.details = gameDetails;
        this.gameSettings.setGameTitle(title);
        this.gameSettings.setMapName(mapName);
    }

    void launch(LaunchDetails launchDetails) {
        if (details == null || gameId == null) {
            throw new IllegalStateException("Game details not set");
        }

        LOG.debug("Sending launch response with details {}", launchDetails);

        LobbyMode lobbyMode = switch (details.gameType()) {
            case COOP, CUSTOM, TUTORIAL -> LobbyMode.DEFAULT_LOBBY;
            case MATCHMAKER, TOURNAMENT -> LobbyMode.AUTO_LOBBY;
        };
        GameMessage.GameLaunchResponse gameLaunchResponse = new GameMessage.GameLaunchResponse(gameId,
                                                                                               gameSettings.getGameTitle(),
                                                                                               details.featuredMod(),
                                                                                               lobbyMode,
                                                                                               details.gameType(),
                                                                                               details.leaderboard(),
                                                                                               gameSettings.getMap()
                                                                                                           .mapName(),
                                                                                               launchDetails.mapPosition(),
                                                                                               launchDetails.expectedPlayers(),
                                                                                               launchDetails.gameOptions(),
                                                                                               launchDetails.team(),
                                                                                               launchDetails.faction());


        long sessionId = launchDetails.sessionId();
        GamePlayer player = launchDetails.player();
        playerIdMap.put(player.id(), new PlayerDetails(player.id(), player.name(), sessionId, PlayerGameState.CONNECTING));

        lobbyEmitter.send(OutboundGameMessage.forSession(sessionId, gameLaunchResponse));
    }

    boolean updatePlayerSession(int playerId, long sessionId) {
        PlayerDetails playerDetails = playerIdMap.get(playerId);
        if (playerDetails == null) {
            LOG.warn("Trying to update session for player that is not connected to the game");
            return false;
        }
        playerDetails.setSessionId(sessionId);
        return true;
    }

    boolean isJoinable() {
        return details != null && switch (details.gameType()) {
            case COOP, CUSTOM -> true;
            default -> false;
        } && state == State.LOBBY;
    }

    boolean passwordMatches(@Nullable String password) {
        return details != null && (details.password() == null || details.password().equals(password));
    }

    boolean isVisibleToPlayer(GamePlayer player) {
        return playerGameFilter.isVisible(player.id(), leaderboard -> gameHandlerRepository.loadPlayerRating(player.id(), leaderboard));
    }


    @MDCPopulated
    void accept(InboundGPGMessage<GPGMessage.Client> message) {
        gpgMessageExecutor.execute(() -> {
            MDC.put(MDCInterceptor.SESSION_ID, String.valueOf(message.sessionId()));
            MDC.put(MDCInterceptor.PLAYER_ID, String.valueOf(message.playerId()));
            MDC.put(MDCInterceptor.GAME_ID, String.valueOf(gameId));
            try {
                handleGPGMessage(message);
            } finally {
                MDC.remove(MDCInterceptor.SESSION_ID);
                MDC.remove(MDCInterceptor.PLAYER_ID);
                MDC.remove(MDCInterceptor.GAME_ID);
            }
        });
    }

    private void handleGPGMessage(InboundGPGMessage<GPGMessage.Client> message) {
        if (details == null || gameId == null) {
            throw new IllegalStateException("GPG message received before game details set");
        }

        LOG.debug("Received gpg message: {}", message.message().content());
        long sessionId = message.sessionId();
        int playerId = message.playerId();
        PlayerDetails player = playerIdMap.get(playerId);
        if (player == null) {
            throw new IllegalStateException("GPG message received for player not associated with game");
        }

        int hostId = details.host().id();
        boolean isHost = playerId == hostId;
        switch (message.message().content()) {
            case GPGMessage.Client.GameSetup gameSetup -> {
                if (!isHost) {
                    LOG.warn("Game setup message received from non host {}", gameSetup);
                    return;
                }

                gameSettings.handleGameSetup(gameSetup);
                markDirty();
            }
            case GPGMessage.EnforceRating _ -> {}
            case GPGMessage.Bottleneck bottleneck -> LOG.debug("Bottleneck detected: {}", bottleneck);
            case GPGMessage.BottleneckCleared _ -> LOG.debug("Bottleneck cleared");
            case GPGMessage.Desync _ -> desyncs.increment();
            case GPGMessage.Disconnected disconnected -> LOG.debug("Player disconnected: {}", disconnected);
            case GPGMessage.GameEnded _ -> {
                player.setState(PlayerGameState.ENDED);
                endIfFinished();
            }
            case GPGMessage.GameFull _ -> LOG.debug("Game is full");
            case GPGMessage.GameResult gameResult -> {
                int army = gameResult.army();
                ResultDetails result = gameResult.result();

//                String[] parts = result.split(" ");
//                if (parts.length >= 2) {
//                    String resultType = parts[parts.length - 2];
//                    int score = Integer.parseInt(parts[parts.length - 1]);

                //            game.addResult(
                //                sessionController.getPlayer().getId(),
                //                army,
                //                resultType,
                //                score
                //            );
//                }
            }
            case GPGMessage.GameState(GPGMessage.GameState.State messageState) -> {
                switch (messageState) {
                    case GPGMessage.GameState.State.Known.IDLE -> {}
                    case GPGMessage.GameState.State.Known.LOBBY -> {
                        if (isHost) {
                            hostGame(sessionId);
                        } else {
                            PlayerDetails hostDetails = playerIdMap.get(hostId);
                            if (hostDetails == null) {
                                lobbyEmitter.send(OutboundGameMessage.forSession(player.getSessionId(),
                                                                                 new GameMessage.GameJoinFailed(
                                                                                         "host_left_game", gameId)));
                            } else {
                                joinGame(hostDetails, player);
                            }
                        }
                        player.setState(PlayerGameState.CONNECTED);
                        markDirty();
                    }
                    case GPGMessage.GameState.State.Known.LAUNCHING -> {
                        if (!isHost) {
                            LOG.debug("Non host launching");
                            return;
                        }

                        if (state != State.LOBBY) {
                            LOG.warn("Tried to launch game that is not in lobby state");
                            return;
                        }

                        LOG.info("Launching game");
                        launchedAt = Instant.now();
                        state = State.LIVE;
                        playerGameFilter = PlayerGameFilter.VISIBLE_TO_ALL;
                        gameSettings.markLaunched();
                        markDirty();
                    }
                    case GPGMessage.GameState.State.Known.ENDED -> {
                        LOG.info("player left");
                        removePlayer(playerId);
                        markDirty();
                        endIfFinished();
                    }
                    case GPGMessage.GameState.State.Unknown(String unknownState) ->
                            LOG.warn("Unknown game state received {}", unknownState);
                }
            }
            case GPGMessage.IceMsg iceMsg -> {
                int receiverId = iceMsg.receiverId();
                String iceMessage = iceMsg.iceMessage();
                LOG.debug("ICE message for player {}: {}", receiverId, iceMessage);
                PlayerDetails playerDetails = playerIdMap.get(receiverId);
                if (playerDetails == null) {
                    return;
                }
                gpgEmitter.send(OutboundGPGMessage.forSession(playerDetails.getSessionId(),
                                                              new GPGMessage.IceMsg(playerId, iceMessage)));
            }
            case GPGMessage.JsonStats(JsonStatsPayload jsonStats) -> {
                //        try {
                //            game.reportArmyStats(stats);
                //        } catch (Exception e) {
                //            LOG.warnf("Failed to parse JSON stats: %s", e.getMessage());
                //        }
            }
            case GPGMessage.LaunchStatus launchStatus -> LOG.info("Launch status: {}", launchStatus.status());
            case GPGMessage.OperationComplete operationComplete -> {
                boolean primary = operationComplete.primary();
                boolean secondary = operationComplete.secondary();
                String delta = operationComplete.time();

                LOG.info("Operation complete: primary={}, secondary={}, delta={}", primary, secondary, delta);
            }
            case GPGMessage.Rehost _ -> LOG.info("Game rehost requested");
            case GPGMessage.TeamkillHappened teamkillHappened -> LOG.info("Teamkill happened: {}", teamkillHappened);
            case GPGMessage.TeamkillReport teamkillReport -> LOG.info("Teamkill reported: {}", teamkillReport);
            case GPGMessage.Chat _ -> {}
            case GPGMessage.Beat _ -> {}
            case GPGMessage.DisconnectedPeer _ -> {}
            case GPGMessage.EstablishedPeer _ -> {}
            case GPGMessage.ProcessNatPacket _ -> {}
            case GPGMessage.XMLStats _ -> {}
            case GPGMessage.Unknown unknown -> LOG.info("Received unknown GPG message: {}", unknown);
            case GPGMessage.Invalid invalid -> LOG.info("Received invalid GPG message: {}", invalid);
        }
    }

    private boolean isVisible(int viewingPlayerId, Function<String, Optional<LeaderboardRating>> playerRatingFunction) {
        if (details == null) {
            return false;
        }

        if (playerIdMap.containsKey(viewingPlayerId)) {
            return true;
        }

        if (!details.visibility().isVisibleTo(viewingPlayerId)) {
            return false;
        }

        if (!details.ratingRange().enforce()) {
            return true;
        }

        double rating = playerRatingFunction.apply(details.leaderboard())
                                            .map(LeaderboardRating::displayedRating)
                                            .orElse(0d);
        return details.ratingRange().inRange(rating);
    }

    private void removePlayer(int playerId) {
        PlayerDetails playerDetails = playerIdMap.remove(playerId);
        if (state == State.LOBBY) {
            gameSettings.removePlayer(playerId);
        }
        if (playerDetails == null) {
            return;
        }
        gameTracker.removeConnectionToGame(playerDetails.getId(), this);
    }

    private void joinGame(PlayerDetails hostDetails, PlayerDetails player) {
        LOG.info("Joining game");

        gpgEmitter.send(OutboundGPGMessage.forSession(player.getSessionId(),
                                                      new GPGMessage.JoinGame(hostDetails.getName(),
                                                                              hostDetails.getId())));
        gpgEmitter.send(OutboundGPGMessage.forSession(hostDetails.getSessionId(),
                                                      new GPGMessage.ConnectToPeer(player.getName(), player.getId(),
                                                                                   true)));
        playerIdMap.forEach((peerId, peerDetails) -> {
            if (peerId == hostDetails.getId() || peerDetails.getState() != PlayerGameState.CONNECTED) {
                return;
            }

            gpgEmitter.send(OutboundGPGMessage.forSession(player.getSessionId(),
                                                          new GPGMessage.ConnectToPeer(peerDetails.getName(), peerId,
                                                                                       true)));
            gpgEmitter.send(OutboundGPGMessage.forSession(peerDetails.getSessionId(),
                                                          new GPGMessage.ConnectToPeer(player.getName(), player.getId(),
                                                                                       false)));
        });
    }

    private void hostGame(long sessionId) {
        LOG.info("Hosting game");
        gpgEmitter.send(
                OutboundGPGMessage.forSession(sessionId, new GPGMessage.HostGame(gameSettings.getMap().mapName())));
        hostedAt = Instant.now();
        state = State.LOBBY;
        playerGameFilter = this::isVisible;
    }

    private void endIfFinished() {
        if (state == State.ENDED || hostPresentInLobby() || playersStillConnected()) {
            return;
        }

        switch (state) {
            case INITIALIZING -> LOG.info("Game cancelled pre initalization");
            case LOBBY -> LOG.info("Game cancelled pre launch");
            case LIVE -> {
                LOG.info("Game finished normally");
                if (desyncs.sum() > 20) {
                    // mark invalid
                }
            }
        }
        closeGame();
    }

    private void closeGame() {
        state = State.ENDED;

        playerIdMap.values()
                   .forEach(playerDetails -> gameTracker.removeConnectionToGame(playerDetails.getId(), this));
        gameTracker.removeGame(this);
        gpgMessageExecutor.shutdown();
        GameInfoWithFilter gameInfoWithFilter = createGameInfo();
        if (gameInfoWithFilter == null) {
            return;
        }

        playerTracker.stageUpdate(gameInfoWithFilter);
    }

    private boolean playersStillConnected() {
        return !playerIdMap.values().stream().map(PlayerDetails::getState).allMatch(PlayerGameState.ENDED::equals);
    }

    private boolean hostPresentInLobby() {
        return details == null ||
               (state == State.LOBBY &&
                playerIdMap.get(details.host().id()) != null &&
                playerIdMap.get(details.host().id()).getState() != PlayerGameState.ENDED);
    }

    private void markDirty() {
        GameInfoWithFilter gameInfo = createGameInfo();
        if (gameInfo == null) {
            return;
        }

        playerTracker.stageUpdate(gameInfo);
    }

    private enum State {
        INITIALIZING, LOBBY, LIVE, ENDED
    }

    private enum PlayerGameState {
        CONNECTING, CONNECTED, ENDED, DISCONNECTED
    }

    @AllArgsConstructor
    @Setter
    @Getter
    private static class PlayerDetails {
        private final int id;
        private final String name;

        private long sessionId;
        private PlayerGameState state;
    }
}
