package com.faforever.server.player;

import com.faforever.server.domain.LeaderboardRatingEntity;
import com.faforever.server.domain.PlayerEntity;
import com.faforever.server.game.GameStateTracker;
import com.faforever.server.interceptor.cdi.MDCInterceptor;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.service.PlayerSessionState;
import com.faforever.server.micrometer.PlayerSessionStateResolver;
import com.faforever.server.rating.LeaderboardRating;
import com.faforever.server.rating.LeaderboardRatingRepository;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.MeterTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RunOnVirtualThread
public class PlayerStateTracker {

    private final GameStateTracker gameStateTracker;

    private final Emitter<Map<String, String>> playerConnectionsRequestEmitter;

    private final PlayerRepository playerRepository;
    private final LeaderboardRatingRepository leaderboardRatingRepository;

    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, Player> playerIdMap;

    @Getter(AccessLevel.PACKAGE)
    private final Map<Long, Player> sessionPlayerMap;
    @Getter(AccessLevel.PACKAGE)
    private final Map<Long, Instant> sessionExpirationMap;
    @Getter(AccessLevel.PACKAGE)
    private final Map<Player, Set<Long>> playerSessionMap = new ConcurrentHashMap<>();

    private Instant lastRatingUpdateTime = Instant.now();

    PlayerStateTracker(GameStateTracker gameStateTracker, @Channel("player-session-request") Emitter<Map<String, String>> playerConnectionsRequestEmitter,
                       PlayerRepository playerRepository,
                       LeaderboardRatingRepository leaderboardRatingRepository, MeterRegistry meterRegistry) {
        this.gameStateTracker = gameStateTracker;
        this.playerConnectionsRequestEmitter = playerConnectionsRequestEmitter;
        this.playerRepository = playerRepository;
        this.leaderboardRatingRepository = leaderboardRatingRepository;
        this.playerIdMap = meterRegistry.gaugeMapSize("faf.players", Tags.of("state", "online"),
                                                      new ConcurrentHashMap<>());
        this.sessionPlayerMap = meterRegistry.gaugeMapSize("faf.sessions", Tags.of("state", "online"),
                                                           new ConcurrentHashMap<>());
        this.sessionExpirationMap = Objects.requireNonNull(
                meterRegistry.gauge("faf.sessions", Tags.of("state", "expired"), new ConcurrentHashMap<>(),
                                    sessionsExpiredMap -> sessionsExpiredMap.values()
                                                                            .stream()
                                                                            .filter(instant -> instant.isAfter(
                                                                                    Instant.now()))
                                                                            .count()));
    }

    @PostConstruct
    void registerExistingPlayers() {
        playerConnectionsRequestEmitter.send(Map.of());
    }

    @Incoming("player-session-state")
    @Timed(value = "faf.player.session.update", histogram = true)
    @MDCPopulated
    void handlePlayerSessionStatus(@MeterTag(key = "state",
                                             resolver = PlayerSessionStateResolver.class) PlayerSessionState playerSessionStatus) {
        switch (playerSessionStatus.state()) {
            case PlayerSessionState.State.New(Duration secondsToExpiry) ->
                    registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                          secondsToExpiry);

            case PlayerSessionState.State.Existing(Duration secondsToExpiry) ->
                    registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                          secondsToExpiry);
            case PlayerSessionState.State.Gone() ->
                    unregisterPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId());
        }
    }

    private void registerPlayerSession(long sessionId, int playerId,
                                       Duration secondsToExpiry) {
        LOG.atDebug()
           .addKeyValue(MDCInterceptor.SESSION_ID, sessionId)
           .addKeyValue(MDCInterceptor.PLAYER_ID, playerId)
           .log("Registering session");
        Player player = playerIdMap.computeIfAbsent(playerId, key -> {
            PlayerEntity playerEntity = playerRepository.loadPlayer(key)
                                                        .orElseThrow(() -> new IllegalStateException(
                                                                "No user with id %d".formatted(playerId)));

            Set<LeaderboardRating> leaderboardRatings = leaderboardRatingRepository.loadPlayerRatings(
                    playerEntity.getId()).stream().map(LeaderboardRating::from).collect(Collectors.toSet());

            Player loadedPlayer = Player.from(playerEntity);
            loadedPlayer.updateLeaderboardRatings(leaderboardRatings);
            return loadedPlayer;
        });
        Player existingPlayer = sessionPlayerMap.putIfAbsent(sessionId, player);
        if (existingPlayer != null && existingPlayer != player) {
            throw new IllegalStateException(
                    "Session already has an existing player of id %d".formatted(existingPlayer.getId()));
        }
        playerSessionMap.compute(player, (_, sessions) -> {
            if (sessions == null) {
                sessions = ConcurrentHashMap.newKeySet();
            }
            sessions.add(sessionId);
            return sessions;
        });

        sessionExpirationMap.put(sessionId, Instant.now().plus(secondsToExpiry));

        gameStateTracker.sendGamesToPlayer(sessionId, player);
    }

    private void unregisterPlayerSession(long sessionId, int playerId) {
        LOG.atInfo()
           .addKeyValue(MDCInterceptor.SESSION_ID, sessionId)
           .addKeyValue(MDCInterceptor.PLAYER_ID, playerId)
           .log("Removing session");
        playerIdMap.computeIfPresent(playerId, (_, player) -> {
            sessionExpirationMap.remove(sessionId);

            if (!sessionPlayerMap.remove(sessionId, player)) {
                return player;
            }

            Set<Long> remainingSessions = playerSessionMap.computeIfPresent(player, (_, sessions) -> {
                if (sessions.remove(sessionId) && sessions.isEmpty()) {
                    return null;
                } else {
                    return sessions;
                }
            });

            if (remainingSessions == null) {
                player.markOffline();
                return null;
            } else {
                return player;
            }
        });
    }

    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Timed(value = "faf.player.session.remove", histogram = true)
    void removeExpiredSessions() {
        Instant evaluationTime = Instant.now();

        Set<Long> expiredSessionIds = sessionExpirationMap.entrySet()
                                                          .stream()
                                                          .filter(entry -> evaluationTime.isAfter(entry.getValue()))
                                                          .map(Map.Entry::getKey)
                                                          .collect(Collectors.toSet());
        if (expiredSessionIds.isEmpty()) {
            LOG.debug("No expired sessions found");
            return;
        }

        LOG.info("Removing {} expired sessions", expiredSessionIds.size());
        expiredSessionIds.forEach(expiredSessionId -> {
            Player player = sessionPlayerMap.get(expiredSessionId);
            if (player != null) {
                unregisterPlayerSession(expiredSessionId, player.getId());
            }
            sessionExpirationMap.remove(expiredSessionId);
        });
    }

    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Timed(value = "faf.player.leaderboard.update", histogram = true)
    void updatePlayerLeaderboardRatings() {
        Instant currentRatingUpdateTime = lastRatingUpdateTime;
        lastRatingUpdateTime = Instant.now();
        Set<Integer> playerIds = Set.copyOf(playerIdMap.keySet());
        if (playerIds.isEmpty()) {
            LOG.debug("No players connected");
            return;
        }

        Map<Integer, Set<LeaderboardRating>> playerIdRatingMap = leaderboardRatingRepository.loadUpdatedLeaderboardRatings(
                                                                                                    playerIds, currentRatingUpdateTime)
                                                                                            .stream()
                                                                                            .collect(
                                                                                                    Collectors.groupingBy(
                                                                                                            LeaderboardRatingEntity::getPlayerId,
                                                                                                            Collectors.mapping(
                                                                                                                    LeaderboardRating::from,
                                                                                                                    Collectors.toSet())));

        if (playerIdRatingMap.isEmpty()) {
            LOG.debug("No updated ratings found");
            return;
        }

        LOG.info("Updating ratings for {} players", playerIdRatingMap.size());
    }

    public @Nullable Player getPlayerForSession(long sessionId) {
        return sessionPlayerMap.get(sessionId);
    }

    public @Nullable Player getPlayer(int playerId) {
        return playerIdMap.get(playerId);
    }

    public Set<Integer> getPlayerIds() {
        return Set.copyOf(playerIdMap.keySet());
    }
}
