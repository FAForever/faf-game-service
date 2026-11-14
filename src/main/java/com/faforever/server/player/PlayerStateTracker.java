package com.faforever.server.player;

import com.faforever.server.game.GameStateTracker;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.service.PlayerSessionState;
import com.faforever.server.micrometer.PlayerSessionStateResolver;
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

    private final PlayerStateRepository playerStateRepository;

    @Getter(AccessLevel.PACKAGE)
    private final Map<Long, Player> sessionPlayerMap;
    @Getter(AccessLevel.PACKAGE)
    private final Map<Long, Instant> sessionExpirationMap;

    private Instant lastRatingUpdateTime = Instant.now();

    PlayerStateTracker(GameStateTracker gameStateTracker,
                       @Channel("player-session-request") Emitter<Map<String, String>> playerConnectionsRequestEmitter,
                       PlayerStateRepository playerStateRepository, MeterRegistry meterRegistry) {
        this.gameStateTracker = gameStateTracker;
        this.playerConnectionsRequestEmitter = playerConnectionsRequestEmitter;
        this.playerStateRepository = playerStateRepository;
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
                    unregisterPlayerSession(playerSessionStatus.sessionId());
        }
    }

    private void registerPlayerSession(long sessionId, int playerId, Duration secondsToExpiry) {
        LOG.debug("Registering session");
        sessionPlayerMap.computeIfAbsent(sessionId, _ -> {
            Player loadedPlayer = playerStateRepository.loadPlayer(playerId)
                                                       .orElseThrow(() -> new IllegalStateException(
                                                               "No user with id %d".formatted(playerId)));

            loadedPlayer.updateLeaderboardRatings(playerStateRepository.loadPlayerRatings(loadedPlayer.getId()));
            gameStateTracker.sendGamesToPlayer(sessionId, loadedPlayer);
            return loadedPlayer;
        });

        sessionExpirationMap.put(sessionId, Instant.now().plus(secondsToExpiry));
    }

    private void unregisterPlayerSession(long sessionId) {
        LOG.debug("Removing session");
        sessionPlayerMap.remove(sessionId);
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
                unregisterPlayerSession(expiredSessionId);
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
        Set<Integer> playerIds = sessionPlayerMap.values().stream().map(Player::getId).collect(Collectors.toSet());
        if (playerIds.isEmpty()) {
            LOG.debug("No players connected");
            return;
        }

        Map<Integer, Set<LeaderboardRating>> playerIdRatingMap = playerStateRepository.loadUpdatedLeaderboardRatings(
                                                                                              playerIds, currentRatingUpdateTime)
                                                                                      .stream()
                                                                                      .collect(Collectors.groupingBy(
                                                                                              PlayerLeaderboardRating::playerId,
                                                                                              Collectors.mapping(
                                                                                                      PlayerLeaderboardRating::leaderboardRating,
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

    public Set<Player> getPlayers() {
        return Set.copyOf(sessionPlayerMap.values());
    }
}
