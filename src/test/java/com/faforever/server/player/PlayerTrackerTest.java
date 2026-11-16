package com.faforever.server.player;

import com.faforever.server.message.service.PlayerSessionState;
import com.faforever.server.player.dto.LeaderboardRating;
import com.faforever.server.player.dto.PlayerLeaderboardRating;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusComponentTest
class PlayerTrackerTest {

    private static final int PLAYER_ID = 1;
    private static final long SESSION_ID = 0;
    private static final String PLAYER_NAME = "test";
    private static final String CLAN_TAG = "TEST";
    private static final String AVATAR_URL = "temp";
    private static final String AVATAR_DESCRIPTION = "temporary";
    private static final String LEADERBOARD = "leaderboard";
    private static final int LEADERBOARD_MEAN = 1000;
    private static final int LEADERBOARD_DEVIATION = 100;
    private static final String IP_ADDRESS = "127.0.0.1";
    private static final Instant INSTANT = Instant.now();
    private static final String COUNTRY = "USA";

    @Inject
    PlayerTracker playerTracker;

    @InjectMock
    private PlayerTrackerRepository playerTrackerRepository;
    @InjectMock
    private Emitter<Map<String, String>> playerConnectionsRequestEmitter;

    @Test
    void testExistingPlayersRegistered() {
        verify(playerConnectionsRequestEmitter).send(Map.of());
    }

    @Nested
    class Unregistered {

        @Test
        void testRegisterSession() {
            Player player = new Player(PLAYER_ID, PLAYER_NAME);
            when(playerTrackerRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(player));

            Duration secondsToExpiry = Duration.ofSeconds(1);
            playerTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                           new PlayerSessionState.State.New(
                                                                                   secondsToExpiry)));

            assertEquals(INSTANT.plus(secondsToExpiry),
                         playerTracker.getSessionMap().get(SESSION_ID).getExpirationTime());
            assertEquals(PLAYER_ID, playerTracker.getSessionMap().get(SESSION_ID).getPlayerId());
            assertTrue(player.hasSession(SESSION_ID));
        }

        @Test
        void testRegisterSessionBadId() {
            assertThrows(IllegalStateException.class, () -> playerTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.New(Duration.ofSeconds(1)))));
        }

        @Test
        void testUnregisterSession() {
            playerTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS, new PlayerSessionState.State.Gone()));

            assertThat(playerTracker.getSessionMap().keySet(), hasSize(0));
        }
    }

    @Nested
    class Registered {

        final Player player = new Player(PLAYER_ID, PLAYER_NAME);

        @BeforeEach
        void setup() {
            when(playerTrackerRepository.loadPlayer(PLAYER_ID)).thenReturn(Optional.of(player));
            when(playerTrackerRepository.loadPlayerRatings(PLAYER_ID)).thenReturn(
                    List.of(new LeaderboardRating(LEADERBOARD, LEADERBOARD_MEAN,
                                                  LEADERBOARD_DEVIATION)));

            playerTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                           new PlayerSessionState.State.Existing(
                                                                                   Duration.ofSeconds(1))));
        }

        @Test
        void testDoubleRegistrationThrowsWhenDifferentPlayers() {
            when(playerTrackerRepository.loadPlayer(2)).thenReturn(
                    Optional.of(new Player(PLAYER_ID + 2, "other")));
            assertThrows(IllegalStateException.class, () -> playerTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(2, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.New(Duration.ofSeconds(1)))));
            assertThrows(IllegalStateException.class, () -> playerTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(2, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.Existing(Duration.ofSeconds(1)))));
        }

        @Test
        void testDoubleRegistrationDoesNotThrowWhenSamePlayer() {
            assertDoesNotThrow(() -> playerTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.New(Duration.ofSeconds(1)))));
            assertDoesNotThrow(() -> playerTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                           new PlayerSessionState.State.Existing(Duration.ofSeconds(1)))));
        }

        @Test
        void testUnregisterSession() {
            playerTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS, new PlayerSessionState.State.Gone()));

            assertFalse(player.hasSession(SESSION_ID));
            assertFalse(playerTracker.getSessionMap().containsKey(SESSION_ID));
        }

        @Test
        void testUnregisterNonRegisteredSession() {
            playerTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, 2, IP_ADDRESS, new PlayerSessionState.State.Gone()));

            assertTrue(playerTracker.getSessionMap().containsKey(SESSION_ID));
            assertTrue(player.hasSession(SESSION_ID));
        }

        @Test
        void testUnregisterSessionMultipleSessions() {
            Duration secondsToExpiry = Duration.ofSeconds(1);
            long newSessionId = 2;
            playerTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, newSessionId, IP_ADDRESS,
                                                                           new PlayerSessionState.State.New(
                                                                                   secondsToExpiry)));

            Instant expirationTime = INSTANT.plus(secondsToExpiry);
            assertEquals(expirationTime, playerTracker.getSessionMap().get(SESSION_ID).getExpirationTime());
            assertEquals(expirationTime, playerTracker.getSessionMap().get(newSessionId).getExpirationTime());
            assertEquals(PLAYER_ID, playerTracker.getSessionMap().get(SESSION_ID).getPlayerId());
            assertEquals(PLAYER_ID, playerTracker.getSessionMap().get(newSessionId).getPlayerId());
            assertTrue(player.hasSession(SESSION_ID));
            assertTrue(player.hasSession(newSessionId));

            playerTracker.handlePlayerSessionStatus(
                    new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS, new PlayerSessionState.State.Gone()));

            assertEquals(expirationTime, playerTracker.getSessionMap().get(newSessionId).getExpirationTime());
            assertFalse(playerTracker.getSessionMap().containsKey(SESSION_ID));
            assertFalse(playerTracker.getSessionMap().containsKey(SESSION_ID));
            assertEquals(PLAYER_ID, playerTracker.getSessionMap().get(newSessionId).getPlayerId());
            assertTrue(player.hasSession(newSessionId));
            assertFalse(player.hasSession(SESSION_ID));
        }

        @Test
        void testUpdateLeaderboards() {
            double deviation = 10d;
            double mean = 100d;

            when(playerTrackerRepository.loadUpdatedLeaderboardRatings(anyCollection(), any())).thenReturn(
                    List.of(new PlayerLeaderboardRating(PLAYER_ID, new LeaderboardRating(LEADERBOARD, mean,
                                                                                         deviation))));

            playerTracker.updatePlayerLeaderboardRatings();

            assertEquals(Optional.of(new LeaderboardRating(LEADERBOARD, mean, deviation)), player.getRating(LEADERBOARD));
        }

        @Test
        void testUpdateLeaderboardsNoUpdates() {
            playerTracker.updatePlayerLeaderboardRatings();

            assertEquals(Optional.of(new LeaderboardRating(LEADERBOARD, LEADERBOARD_MEAN, LEADERBOARD_DEVIATION)), player.getRating(LEADERBOARD));
        }

        @Test
        void testExpiredSession() {
            playerTracker.handlePlayerSessionStatus(new PlayerSessionState(PLAYER_ID, SESSION_ID, IP_ADDRESS,
                                                                           new PlayerSessionState.State.Existing(
                                                                                   Duration.ofNanos(-1))));

            playerTracker.removeExpiredSessions();

            assertFalse(player.hasSession(SESSION_ID));
            assertThat(playerTracker.getSessionMap().keySet(), hasSize(0));
        }

        @Test
        void testNoExpiredSession() {
            playerTracker.removeExpiredSessions();

            assertThat(playerTracker.getSessionMap().keySet(), contains(SESSION_ID));
            assertTrue(player.hasSession(SESSION_ID));
        }
    }

    @ApplicationScoped
    static class TestMeterRegistry extends SimpleMeterRegistry {}

    static class ClockProducer {
        @Produces
        Clock clock() {
            return Clock.fixed(INSTANT, ZoneOffset.UTC);
        }
    }
}
