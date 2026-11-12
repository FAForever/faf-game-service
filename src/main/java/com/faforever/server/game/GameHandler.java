package com.faforever.server.game;

import com.faforever.server.interceptor.cdi.MDCInterceptor;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.client.GPGMessage;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.client.dto.GameStatus;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.client.dto.JsonStatsPayload;
import com.faforever.server.message.client.dto.LobbyMode;
import com.faforever.server.message.service.InboundGPGMessage;
import com.faforever.server.message.service.OutboundGPGMessage;
import com.faforever.server.message.service.OutboundGameMessage;
import com.faforever.server.player.Player;
import com.faforever.server.rating.LeaderboardRating;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.Dependent;
import lombok.AccessLevel;
import lombok.Getter;
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
import java.util.List;
import java.util.Map;
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

    private final GameStateTracker gameStateTracker;

    private final Emitter<OutboundGPGMessage<GPGMessage.Server>> gpgEmitter;
    private final Emitter<OutboundGameMessage<GameMessage.Server>> lobbyEmitter;
    private final ThreadPoolExecutor gpgMessageExecutor;

    private final Counter gpgDroppedMessageCounter;

    @Getter(AccessLevel.PACKAGE)
    private final int gameId;

    private final GameOptions gameOptions = new GameOptions();

    private final Map<Integer, GamePlayer> playerIdMap = new ConcurrentHashMap<>();
    private final Map<Long, GamePlayer> playerSessionMap = new ConcurrentHashMap<>();

    private final LongAdder desyncs = new LongAdder();

    private @Nullable GameDetails details;
    private @Nullable Instant hostedAt;
    private @Nullable Instant launchedAt;
    private boolean enforceRating = false;

    private String title = "";
    private State state = State.INITIALIZING;
    private String mapName = "";

    GameHandler(GameStateTracker gameStateTracker, MeterRegistry meterRegistry,
                @Channel("lobby-outbound") Emitter<OutboundGPGMessage<GPGMessage.Server>> gpgEmitter,
                @Channel("lobby-outbound") Emitter<OutboundGameMessage<GameMessage.Server>> lobbyEmitter,
                @ConfigProperty(name = "faf.max-message-queue-size", defaultValue = "0") int maxMessageQueueSize) {
        this.gameStateTracker = gameStateTracker;
        this.gpgEmitter = gpgEmitter;
        this.lobbyEmitter = lobbyEmitter;
        gpgDroppedMessageCounter = meterRegistry.counter("faf.gpg.messages.dropped");
        gameId = gameStateTracker.nextGameId();
        this.gpgMessageExecutor = createMessageExecutor(
                maxMessageQueueSize == 0 ? Integer.MAX_VALUE : maxMessageQueueSize);
    }

    @MDCPopulated
    void accept(InboundGPGMessage<GPGMessage.Client> message) {
        gpgMessageExecutor.execute(() -> {
            MDC.put(MDCInterceptor.COMMAND, message.message().content().command());
            MDC.put(MDCInterceptor.SESSION_ID, String.valueOf(message.sessionId()));
            MDC.put(MDCInterceptor.PLAYER_ID, String.valueOf(message.playerId()));
            MDC.put(MDCInterceptor.GAME_ID, String.valueOf(gameId));
            try {
                handleGPGMessage(message);
            } finally {
                MDC.remove(MDCInterceptor.COMMAND);
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
        this.title = title;
        this.mapName = mapName;
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
        GameMessage.GameLaunchResponse gameLaunchResponse = new GameMessage.GameLaunchResponse(gameId, title,
                                                                                               details.featuredMod(),
                                                                                               lobbyMode,
                                                                                               details.gameType(),
                                                                                               details.leaderboard(),
                                                                                               mapName,
                                                                                               launchDetails.mapPosition(),
                                                                                               launchDetails.expectedPlayers(),
                                                                                               launchDetails.gameOptions(),
                                                                                               launchDetails.team(),
                                                                                               launchDetails.faction());


        long sessionId = launchDetails.sessionId();
        GamePlayer player = launchDetails.player();
        playerSessionMap.put(sessionId, player);
        playerIdMap.put(player.playerId(), player);

        lobbyEmitter.send(OutboundGameMessage.forSession(sessionId, gameLaunchResponse));
    }

    private void handleGPGMessage(InboundGPGMessage<GPGMessage.Client> message) {
        LOG.debug("Received gpg message: {}", message.message());
        if (details == null) {
            throw new IllegalStateException("GPG message received before game details set");
        }

        long sessionId = message.sessionId();
        int playerId = message.playerId();
        boolean isHost = playerId == details.host().playerId();
        switch (message.message().content()) {
            case GPGMessage.AIOption aiOption when isHost -> {
                gameStateTracker.markDirty(this);
            }
            case GPGMessage.ClearSlot clearSlot when isHost -> {
                int slot = clearSlot.slot();
                //        game.clearSlot(slot);
            }
            case GPGMessage.PlayerOption playerOption when isHost -> {
                int optionPlayerId = playerOption.playerId();
                String key = playerOption.key();
                Object value = playerOption.value();

                gameStateTracker.markDirty(this);
            }
            case GPGMessage.GameOption gameOption when isHost -> {
                gameStateTracker.markDirty(this);
            }
            case GPGMessage.GameMods gameMods when isHost -> {
                switch (gameMods) {
                    case GPGMessage.GameMods.Activated(int count) -> {

                    }
                    case GPGMessage.GameMods.Uids(Collection<String> uids) -> {

                    }
                }
            }
            case GPGMessage.Client.HostOnly hostOnly ->
                    LOG.warn("Host only message received from non host {}", hostOnly);
            case GPGMessage.EnforceRating _ -> {
                enforceRating = true;
                gameStateTracker.markDirty(this);
            }
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
                String result = gameResult.result();

                String[] parts = result.split(" ");
                if (parts.length >= 2) {
                    String resultType = parts[parts.length - 2];
                    int score = Integer.parseInt(parts[parts.length - 1]);

                    //            game.addResult(
                    //                sessionController.getPlayer().getId(),
                    //                army,
                    //                resultType,
                    //                score
                    //            );
                }
            }
            case GPGMessage.GameState gameState -> {
                GPGMessage.GameState.State state = gameState.state();

                switch (state) {
                    case GPGMessage.GameState.State.Known.IDLE -> {
                    }
                    case GPGMessage.GameState.State.Known.LOBBY -> {
                        if (isHost && hostedAt == null) {
                            LOG.info("Hosting game");
                            gpgEmitter.send(OutboundGPGMessage.forSession(sessionId,
                                                                          new GPGMessage.HostGame(List.of(mapName))));
                            hostedAt = Instant.now();
                            gameStateTracker.markDirty(this);
                        }
                    }
                    case GPGMessage.GameState.State.Known.LAUNCHING -> {
                        //        if (!isHost() || game.getState() != GameState.LOBBY) {
                        //            return;
                        //        }
                        LOG.info("Launching game");
                        launchedAt = Instant.now();
                    }
                    case GPGMessage.GameState.State.Unknown unknown -> {

                    }
                }
            }
            case GPGMessage.IceMsg iceMsg -> {
                int receiverId = Integer.parseInt(iceMsg.args().getFirst().toString());
                String iceMessage = iceMsg.args().get(1).toString();
                LOG.debug("ICE message for player {}: {}", receiverId, iceMessage);
            }
            case GPGMessage.JsonStats jsonStats -> {
                JsonStatsPayload stats = jsonStats.payload();
                //        try {
                //            game.reportArmyStats(stats);
                //        } catch (Exception e) {
                //            LOG.warnf("Failed to parse JSON stats: %s", e.getMessage());
                //        }
            }
            case GPGMessage.LaunchStatus launchStatus -> {
                LOG.debug("Launch status: {}", launchStatus.status());
            }
            case GPGMessage.OperationComplete operationComplete -> {
                boolean primary = operationComplete.primary();
                boolean secondary = operationComplete.secondary();
                String delta = operationComplete.time();

                LOG.info("Operation complete: primary={}, secondary={}, delta={}", primary, secondary, delta);
            }
            case GPGMessage.Rehost _ -> LOG.debug("Game rehosted");
            case GPGMessage.TeamkillHappened teamkillHappened ->
                    LOG.info("Teamkill happened: {}", teamkillHappened);
            case GPGMessage.TeamkillReport teamkillReport -> LOG.info("Teamkill reported: {}", teamkillReport);
            case GPGMessage.Chat _ -> {}
            case GPGMessage.Beat _ -> {}
            case GPGMessage.DisconnectedPeer _ -> {}
            case GPGMessage.EstablishedPeer _ -> {}
            case GPGMessage.ProcessNatPacket _ -> {}
            case GPGMessage.XMLStats _ -> {}
            case GPGMessage.Unknown unknown ->
                    LOG.info("Received unknown GPG message: {}", unknown);
            case GPGMessage.Invalid invalid -> LOG.info("Received invalid GPG message: {}", invalid);
        }
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
        return details.ratingRange().inRange(
                       player.getRating(details.leaderboard()).map(LeaderboardRating::displayedRating).orElse(0d));
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
        Map<Integer, Set<String>> teams = new HashMap<>();
        Set<GameInfo.Team> teamIds = new HashSet<>();
//        teamMap.forEach((teamId, players) -> {
//            teams.put(teamId, players.stream().map(GamePlayer::name).collect(Collectors.toSet()));
//            teamIds.add(
//                    new GameInfo.Team(teamId, players.stream().map(GamePlayer::playerId).collect(Collectors.toSet())));
//        });

        RatingRange ratingRange = details.ratingRange();
        return new GameInfo(gameId, title, details.host().name(), details.gameType(), 0, 0,
                            switch (details.visibility()) {
                                case Visibility.Private _ -> GameVisibility.PRIVATE;
                                case Visibility.Public _ -> GameVisibility.PUBLIC;
                            }, details.password() != null, gameStatus, details.featuredMod(), details.leaderboard(),
                            Map.of(), "", "", hostedAt, launchedAt == null ? null : launchedAt.getEpochSecond(),
                            details.leaderboard(), ratingRange.min(), ratingRange.max(), enforceRating, teams, teamIds);
    }

    public enum State {
        INITIALIZING, LOBBY, LIVE, ENDED
    }
}
