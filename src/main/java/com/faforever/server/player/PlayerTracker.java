package com.faforever.server.player;

import com.faforever.server.game.GameTracker;
import com.faforever.server.interceptor.cdi.MDCInterceptor;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.client.GameMessage;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.service.OutboundGameMessage;
import com.faforever.server.message.service.PlayerSessionState;
import com.faforever.server.micrometer.PlayerSessionStateResolver;
import com.faforever.server.player.dto.LeaderboardRating;
import com.faforever.server.player.dto.PlayerLeaderboardRating;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.MeterTag;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RunOnVirtualThread
public class PlayerTracker {

    private final GameTracker gameTracker;

    private final Emitter<OutboundGameMessage<GameMessage.Server>> outboundEmitter;

    private final Emitter<Map<String, String>> playerConnectionsRequestEmitter;

    private final Clock clock;

    private final PlayerTrackerRepository playerTrackerRepository;

    private final Map<Integer, Player> playerIdMap;

    @Getter(AccessLevel.PACKAGE)
    private final Map<Long, SessionDetails> sessionMap;
    @Getter(AccessLevel.PACKAGE)
    private final Map<Integer, GameInfoWithFilter> dirtyGames = new ConcurrentHashMap<>();

    private Instant lastRatingUpdateTime = Instant.now();

    PlayerTracker(GameTracker gameTracker,
                  @Channel("lobby-outbound") Emitter<OutboundGameMessage<GameMessage.Server>> outboundEmitter,
                  @Channel("player-session-request") Emitter<Map<String, String>> playerConnectionsRequestEmitter,
                  Clock clock, PlayerTrackerRepository playerTrackerRepository, MeterRegistry meterRegistry) {
        this.gameTracker = gameTracker;
        this.outboundEmitter = outboundEmitter;
        this.playerConnectionsRequestEmitter = playerConnectionsRequestEmitter;
        this.clock = clock;
        this.playerTrackerRepository = playerTrackerRepository;
        this.playerIdMap = meterRegistry.gaugeMapSize("faf.players", Tags.of("state", "online"),
                                                      new ConcurrentHashMap<>());
        this.sessionMap = meterRegistry.gaugeMapSize("faf.sessions", Tags.of("state", "online"),
                                                     new ConcurrentHashMap<>());
        Gauge.builder("faf.sessions", () -> sessionMap.values()
                                                      .stream()
                                                      .map(SessionDetails::getExpirationTime)
                                                      .filter(instant -> instant.isAfter(clock.instant()))
                                                      .count()).tag("state", "expired").register(meterRegistry);
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
            case PlayerSessionState.State.New(Duration secondsToExpiry) -> {
                Player player = registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                                      secondsToExpiry);
                sendActiveGames(playerSessionStatus.sessionId(), player);
                LOG.info("Registered new session");
            }

            case PlayerSessionState.State.Existing(Duration secondsToExpiry) ->
                    registerPlayerSession(playerSessionStatus.sessionId(), playerSessionStatus.playerId(),
                                          secondsToExpiry);
            case PlayerSessionState.State.Gone() -> unregisterSession(playerSessionStatus.sessionId());
        }
    }

    private void sendActiveGames(long sessionId, Player player) {
        GameMessage.GameInfoList visibleGames = gameTracker.getActiveGames()
                                                           .stream()
                                                           .filter(gameInfoWithFilter -> gameInfoWithFilter.filter()
                                                                                                           .isVisible(
                                                                                                                   player.getId(),
                                                                                                                   player::getRating))
                                                           .map(GameInfoWithFilter::gameInfo)
                                                           .collect(Collectors.collectingAndThen(Collectors.toSet(),
                                                                                                 GameMessage.GameInfoList::new));
        outboundEmitter.send(OutboundGameMessage.forSession(sessionId, visibleGames));
    }

    private Player registerPlayerSession(long sessionId, int playerId, Duration secondsToExpiry) {
        LOG.debug("Registering session");
        SessionDetails sessionDetails = sessionMap.computeIfAbsent(sessionId, _ -> new SessionDetails(playerId,
                                                                                                      clock.instant()
                                                                                                           .plus(secondsToExpiry)));
        if (sessionDetails.getPlayerId() != playerId) {
            throw new IllegalStateException(
                    "Session already has an existing player of id %d".formatted(sessionDetails.getPlayerId()));
        }
        sessionDetails.setExpirationTime(clock.instant().plus(secondsToExpiry));

        return playerIdMap.compute(playerId, (id, player) -> {
            if (player == null) {
                player = initializePlayer(id);
            }
            player.addSession(sessionId);
            return player;
        });
    }

    private Player initializePlayer(int playerId) {
        Player player = playerTrackerRepository.loadPlayer(playerId)
                                               .orElseThrow(() -> new IllegalStateException(
                                                       "No user with id %d".formatted(playerId)));

        Collection<LeaderboardRating> leaderboardRatings = playerTrackerRepository.loadPlayerRatings(playerId);

        player.updateLeaderboardRatings(leaderboardRatings);
        return player;
    }

    private void unregisterSession(long sessionId) {
        LOG.info("Removing session");
        SessionDetails sessionDetails = sessionMap.remove(sessionId);
        if (sessionDetails == null) {
            LOG.warn("No player associated with session");
            return;
        }

        playerIdMap.computeIfPresent(sessionDetails.getPlayerId(), (_, player) -> {
            player.removeSession(sessionId);
            if (!player.hasAnySession()) {
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
        Instant evaluationTime = clock.instant();

        Set<Long> expiredSessionIds = sessionMap.entrySet()
                                                .stream()
                                                .filter(entry -> evaluationTime.isAfter(
                                                        entry.getValue().getExpirationTime()))
                                                .map(Map.Entry::getKey)
                                                .collect(Collectors.toSet());
        if (expiredSessionIds.isEmpty()) {
            LOG.debug("No expired sessions found");
            return;
        }

        LOG.info("Removing {} expired sessions", expiredSessionIds.size());
        expiredSessionIds.forEach(expiredSessionId -> {
            MDC.put(MDCInterceptor.SESSION_ID, String.valueOf(expiredSessionId));
            SessionDetails sessionDetails = sessionMap.get(expiredSessionId);
            if (sessionDetails != null) {
                MDC.put(MDCInterceptor.PLAYER_ID, String.valueOf(sessionDetails.getPlayerId()));
            }

            unregisterSession(expiredSessionId);

            MDC.remove(MDCInterceptor.SESSION_ID);
            MDC.remove(MDCInterceptor.PLAYER_ID);
        });
    }

    @Scheduled(every = "1m", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Timed(value = "faf.player.leaderboard.update", histogram = true)
    void updatePlayerLeaderboardRatings() {
        Instant currentRatingUpdateTime = lastRatingUpdateTime;
        lastRatingUpdateTime = clock.instant();
        Set<Integer> playerIds = Set.copyOf(playerIdMap.keySet());
        if (playerIds.isEmpty()) {
            LOG.debug("No players connected");
            return;
        }

        Map<Integer, Set<LeaderboardRating>> playerIdRatingMap = playerTrackerRepository.loadUpdatedLeaderboardRatings(
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

        playerIdRatingMap.forEach((playerId, newLeaderboardRatings) -> {
            Player player = playerIdMap.get(playerId);
            if (player == null) {
                return;
            }

            player.updateLeaderboardRatings(newLeaderboardRatings);
        });
    }

    @Scheduled(every = "1s", skipExecutionIf = Scheduled.ApplicationNotRunning.class,
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sendDirtyGames() {
        Map<Integer, GameInfoWithFilter> frozenDirtyGames = Map.copyOf(dirtyGames);
        if (frozenDirtyGames.isEmpty()) {
            return;
        }

        playerIdMap.values().forEach(player -> {
            Set<GameInfo> visibleGames = dirtyGames.values()
                                                   .stream()
                                                   .filter(game -> game.filter()
                                                                       .isVisible(player.getId(), player::getRating))
                                                   .map(GameInfoWithFilter::gameInfo)
                                                   .collect(Collectors.toSet());
            GameMessage.GameInfoList filteredGameInfoMessage = new GameMessage.GameInfoList(visibleGames);
            if (filteredGameInfoMessage.games().isEmpty()) {
                return;
            }

            outboundEmitter.send(OutboundGameMessage.forPlayer(player.getId(), filteredGameInfoMessage));
        });

        frozenDirtyGames.forEach(dirtyGames::remove);
    }

    public void stageUpdate(GameInfoWithFilter gameInfoWithFilter) {
        dirtyGames.put(gameInfoWithFilter.gameInfo().uid(), gameInfoWithFilter);
    }

    @AllArgsConstructor
    @Setter
    @Getter
    static class SessionDetails {
        private final int playerId;
        private Instant expirationTime;
    }
}
