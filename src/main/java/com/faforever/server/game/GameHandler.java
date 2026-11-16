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
import com.faforever.server.player.Player;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Dependent
class GameHandler {

    private final GameTracker gameTracker;

    private final Emitter<OutboundGPGMessage<GPGMessage.Server>> gpgEmitter;
    private final Emitter<OutboundGameMessage<GameMessage.Server>> lobbyEmitter;
    private final ThreadPoolExecutor gpgMessageExecutor;

    private final GameSettings gameSettings;

    private final Counter gpgDroppedMessageCounter;

    @Getter(AccessLevel.PACKAGE)
    private final int gameId;

    private final Map<Integer, PlayerDetails> playerIdMap = new ConcurrentHashMap<>();

    private final LongAdder desyncs = new LongAdder();

    private @Nullable GameDetails details;
    private @Nullable Instant hostedAt;
    private @Nullable Instant launchedAt;

    private State state = State.INITIALIZING;

    GameHandler(GameTracker gameTracker, MeterRegistry meterRegistry,
                @Channel("lobby-outbound") Emitter<OutboundGPGMessage<GPGMessage.Server>> gpgEmitter,
                @Channel("lobby-outbound") Emitter<OutboundGameMessage<GameMessage.Server>> lobbyEmitter,
                @ConfigProperty(name = "faf.max-message-queue-size", defaultValue = "0") int maxMessageQueueSize,
                GameSettings gameSettings) {
        this.gameTracker = gameTracker;
        this.gpgEmitter = gpgEmitter;
        this.lobbyEmitter = lobbyEmitter;
        this.gpgMessageExecutor = createMessageExecutor(
                maxMessageQueueSize == 0 ? Integer.MAX_VALUE : maxMessageQueueSize);
        this.gameSettings = gameSettings;
        gpgDroppedMessageCounter = meterRegistry.counter("faf.gpg.messages.dropped");
        gameId = gameTracker.nextGameId();
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

    void initializeDetails(String title, String mapName, GameDetails gameDetails) {
        if (details != null) {
            throw new IllegalStateException("Game details already set");
        }

        this.details = gameDetails;
        this.gameSettings.setGameTitle(title);
        this.gameSettings.setMapName(mapName);
    }

    void launch(LaunchDetails launchDetails) {
        if (details == null) {
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
        playerIdMap.put(player.id(), new PlayerDetails(player.id(), player.name(), sessionId, PlayerState.CONNECTING));

        lobbyEmitter.send(OutboundGameMessage.forSession(sessionId, gameLaunchResponse));
    }

    private void handleGPGMessage(InboundGPGMessage<GPGMessage.Client> message) {
        if (details == null) {
            throw new IllegalStateException("GPG message received before game details set");
        }

        LOG.debug("Received gpg message: {}", message.message().content());
        long sessionId = message.sessionId();
        int playerId = message.playerId();
        PlayerDetails player = playerIdMap.get(playerId);
        if (player == null) {
            throw new IllegalStateException("GPG message received for player not associated with game");
        }

        int hostId = details.hostId();
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
//        game.checkGameFinish(sessionController.getPlayer());
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
                            joinGame(hostId, sessionId, player, playerId);
                        }
                        player.setState(PlayerState.CONNECTED);
                        markDirty();
                    }
                    case GPGMessage.GameState.State.Known.LAUNCHING -> {
                        if (!isHost) {
                            return;
                        }

                        if (state != State.LOBBY) {
                            LOG.warn("Tried to launch game that is not in lobby state");
                            return;
                        }

                        LOG.info("Launching game");
                        launchedAt = Instant.now();
                        state = State.LIVE;
                        gameSettings.markLaunched();
                        markDirty();
                    }
                    case GPGMessage.GameState.State.Known.ENDED -> {
                        playerIdMap.remove(playerId);
                        if (state == State.LOBBY) {
                            gameSettings.removePlayer(playerId);
                        }
                        markDirty();
                        endIfFinished();
                    }
                    case GPGMessage.GameState.State.Unknown(String unknownState) -> {
                        LOG.warn("Unknown game state received {}", unknownState);
                    }
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

    private void joinGame(int hostId, long sessionId, PlayerDetails player, int playerId) {
        LOG.info("Joining game");

        PlayerDetails hostDetails = playerIdMap.get(hostId);
        if (hostDetails == null) {
            lobbyEmitter.send(OutboundGameMessage.forSession(sessionId,
                                                                new GameMessage.GameJoinFailed("host_left_game",
                                                                                               gameId)));
            return;
        }

        gpgEmitter.send(
                OutboundGPGMessage.forSession(sessionId, new GPGMessage.JoinGame(hostDetails.getName(), hostId)));
        gpgEmitter.send(OutboundGPGMessage.forSession(hostDetails.getSessionId(),
                                                      new GPGMessage.ConnectToPeer(player.getName(), playerId, true)));
        playerIdMap.forEach((peerId, peerDetails) -> {
            if (peerId == hostId || peerDetails.getState() != PlayerState.CONNECTED) {
                return;
            }

            long peerSessionId = peerDetails.getSessionId();

            gpgEmitter.send(OutboundGPGMessage.forSession(sessionId,
                                                          new GPGMessage.ConnectToPeer(peerDetails.getName(), peerId,
                                                                                       true)));
            gpgEmitter.send(OutboundGPGMessage.forSession(peerSessionId,
                                                          new GPGMessage.ConnectToPeer(player.getName(), playerId,
                                                                                       false)));
        });
    }

    private void hostGame(long sessionId) {
        LOG.info("Hosting game");
        gpgEmitter.send(
                OutboundGPGMessage.forSession(sessionId, new GPGMessage.HostGame(gameSettings.getMap().mapName())));
        hostedAt = Instant.now();
        state = State.LOBBY;
    }

    private void endIfFinished() {
        if (state == State.ENDED || hostPresentInLobby() || playersStillConnected()) {
            return;
        }

        switch (state) {
            case LOBBY -> LOG.info("Game cancelled pre launch");
            case INITIALIZING -> LOG.info("Game cancelled pre initalization");
            case LIVE -> {
                LOG.info("Game finished normally");
                if (desyncs.sum() > 20) {
                    // mark invalid
                }
            }
        }
        state = State.ENDED;
        markDirty();
    }

    private boolean playersStillConnected() {
        return !playerIdMap.values().stream().map(PlayerDetails::getState).allMatch(PlayerState.ENDED::equals);
    }

    private boolean hostPresentInLobby() {
        return details == null ||
               (state == State.LOBBY &&
                playerIdMap.get(details.hostId()) != null &&
                playerIdMap.get(details.hostId()).getState() != PlayerState.ENDED);
    }

    private void markDirty() {
        GameInfo gameInfo = createGameInfo();
        if (gameInfo == null) {
            return;
        }

        gameTracker.stageUpdate(gameInfo, this::isVisibleToPlayer);
        if (state == State.ENDED) {
            gameTracker.removeGame(this);
            gpgMessageExecutor.shutdown();
        }
    }

    @Nullable GameInfo createGameInfo() {
        if (details == null) {
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
                     .filter(playerDetails -> playerDetails.getState() == PlayerState.CONNECTED)
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
        return new GameInfo(gameId, gameSettings.getGameTitle(), details.hostName(), details.gameType(),
                            gameSettings.getNumSlots(), numActivePlayers, visibility, details.password() != null,
                            gameStatus, details.featuredMod(), details.leaderboard(), gameSettings.getSimMods(),
                            map.mapName(), map.mapFilePath(), hostedAt,
                            launchedAt == null ? null : launchedAt.getEpochSecond(), details.leaderboard(),
                            ratingRange.min(), ratingRange.max(), ratingRange.enforce(), teams, teamIds);
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

    boolean isJoinable() {
        return details != null && switch (details.gameType()) {
            case COOP, CUSTOM -> true;
            default -> false;
        } && state == State.LOBBY;
    }

    boolean passwordMatches(@Nullable String password) {
        return details != null && (details.password() == null || details.password().equals(password));
    }

    boolean isVisibleToPlayer(Player player) {
        if (details == null) {
            return false;
        }

        if (state != State.LOBBY) {
            return true;
        }

        if (playerIdMap.containsKey(player.getId())) {
            return true;
        }

        if (!details.visibility().isVisibleTo(player.getId())) {
            return false;
        }

        //TODO: check if 0 is appropriate
        return details.ratingRange()
                      .inRange(player.getRating(details.leaderboard())
                                     .map(LeaderboardRating::displayedRating)
                                     .orElse(0d));
    }

    enum State {
        INITIALIZING, LOBBY, LIVE, ENDED
    }

    private enum PlayerState {
        CONNECTING, CONNECTED, ENDED
    }

    @AllArgsConstructor
    @Setter
    @Getter
    private static class PlayerDetails {
        private final int id;
        private final String name;

        private long sessionId;
        private PlayerState state;
    }
}
