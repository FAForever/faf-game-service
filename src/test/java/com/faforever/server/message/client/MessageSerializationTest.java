package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.Faction;
import com.faforever.server.message.client.dto.GameAccess;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.client.dto.GameStatus;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.client.dto.LobbyMode;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Verifies the wire format of {@link GameMessage}s. The mapper mirrors the application's Jackson configuration
 * ({@code quarkus.jackson.property-naming-strategy: SNAKE_CASE} plus Quarkus' default of ignoring unknown properties).
 */
class MessageSerializationTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static String serializeServer(GameMessage.Server message) {
        return JSON_MAPPER.writerFor(GameMessage.Server.class).writeValueAsString(message);
    }

    private static GameMessage.Client deserializeClient(String json) {
        return JSON_MAPPER.readValue(json, GameMessage.Client.class);
    }

    // ---- Server messages ----

    @Test
    void serializeGameJoinFailed() throws JSONException {
        String json = serializeServer(new GameMessage.GameJoinFailed("Game is full", 42));

        JSONAssert.assertEquals("""
                {
                  "command": "game_join_failed",
                  "reason": "Game is full",
                  "uid": 42
                }
                """, json, JSONCompareMode.STRICT);
    }

    @Test
    void serializeGameLaunchResponse() throws JSONException {
        String json = serializeServer(new GameMessage.GameLaunchResponse(7, "Test Game", "faf",
                LobbyMode.AUTO_LOBBY, GameType.MATCHMAKER, "ladder_1v1", "scmp_007", 2, 2,
                Map.of("Share", "ShareUntilDeath"), 1, Faction.CYBRAN));

        JSONAssert.assertEquals("""
                {
                  "command": "game_launch",
                  "uid": 7,
                  "name": "Test Game",
                  "mod": "faf",
                  "init_mode": 1,
                  "game_type": "matchmaker",
                  "rating_type": "ladder_1v1",
                  "mapname": "scmp_007",
                  "map_position": 2,
                  "expected_players": 2,
                  "game_options": {"Share": "ShareUntilDeath"},
                  "team": 1,
                  "faction": "cybran",
                  "args": []
                }
                """, json, JSONCompareMode.STRICT);
    }

    @Test
    void serializeGameLaunchResponseWithNulls() throws JSONException {
        String json = serializeServer(new GameMessage.GameLaunchResponse(7, "Test Game", "faf",
                LobbyMode.DEFAULT_LOBBY, GameType.CUSTOM, "global", "scmp_007", null, null, Map.of(), null, null));

        JSONAssert.assertEquals("""
                {
                  "command": "game_launch",
                  "uid": 7,
                  "name": "Test Game",
                  "mod": "faf",
                  "init_mode": 0,
                  "game_type": "custom",
                  "rating_type": "global",
                  "mapname": "scmp_007",
                  "map_position": null,
                  "expected_players": null,
                  "game_options": {},
                  "team": null,
                  "faction": null,
                  "args": []
                }
                """, json, JSONCompareMode.STRICT);
    }

    @Test
    void serializeGameInfoList() throws JSONException {
        GameInfo gameInfo = new GameInfo(5, "Test Game", "host", GameType.CUSTOM, 8, 3, GameVisibility.PRIVATE,
                true, GameStatus.OPEN, "faf", "global", Map.of("uid-1", "Some Mod"), "scmp_007",
                "maps/scmp_007.zip", OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC), 1767323045L,
                800, 1200, true);

        String json = serializeServer(new GameMessage.GameInfoList(List.of(gameInfo)));

        JSONAssert.assertEquals("""
                {
                  "command": "game_info",
                  "games": [
                    {
                      "uid": 5,
                      "title": "Test Game",
                      "host": "host",
                      "game_type": "custom",
                      "max_players": 8,
                      "number_of_players": 3,
                      "visibility": "friends",
                      "password_protected": true,
                      "state": "open",
                      "featured_mod": "faf",
                      "rating_type": "global",
                      "sim_mods": {"uid-1": "Some Mod"},
                      "map_name": "scmp_007",
                      "map_file_path": "maps/scmp_007.zip",
                      "hosted_at": "2026-01-02T03:04:05Z",
                      "launched_at": 1767323045,
                      "rating_min": 800,
                      "rating_max": 1200,
                      "enforce_rating_range": true
                    }
                  ]
                }
                """, json, JSONCompareMode.STRICT);
    }

    @Test
    void serializeEmptyGameInfoList() throws JSONException {
        String json = serializeServer(new GameMessage.GameInfoList(List.of()));

        JSONAssert.assertEquals("""
                {"command": "game_info", "games": []}
                """, json, JSONCompareMode.STRICT);
    }

    // ---- Client messages ----

    @Test
    void deserializeHostGameRequest() {
        GameMessage.Client message = deserializeClient("""
                {
                  "command": "game_host",
                  "request_id": "req-1",
                  "mapname": "scmp_007",
                  "title": "Test Game",
                  "mod": "faf",
                  "options": [true, false],
                  "version": 3,
                  "access": "password",
                  "password": "secret",
                  "visibility": "friends",
                  "rating_min": 800,
                  "rating_max": 1200,
                  "enforce_rating_range": true
                }
                """);

        assertThat(message, instanceOf(GameMessage.HostGameRequest.class));
        GameMessage.HostGameRequest request = (GameMessage.HostGameRequest) message;
        assertThat(request.requestId(), is("req-1"));
        assertThat(request.command(), is(GameMessage.HostGameRequest.COMMAND));
        assertThat(request.mapName(), is("scmp_007"));
        assertThat(request.title(), is("Test Game"));
        assertThat(request.featuredMod(), is("faf"));
        assertThat(request.options(), equalTo(new boolean[]{true, false}));
        assertThat(request.version(), is(3));
        assertThat(request.access(), is(GameAccess.PASSWORD));
        assertThat(request.password(), is("secret"));
        assertThat(request.visibility(), is(GameVisibility.PRIVATE));
        assertThat(request.ratingMin(), is(800));
        assertThat(request.ratingMax(), is(1200));
        assertThat(request.enforceRatingRange(), is(true));
    }

    @Test
    void deserializeHostGameRequestWithOptionalFieldsAbsent() {
        GameMessage.Client message = deserializeClient("""
                {
                  "command": "game_host",
                  "request_id": "req-2",
                  "title": "Test Game",
                  "access": "public",
                  "visibility": "public",
                  "enforce_rating_range": false
                }
                """);

        GameMessage.HostGameRequest request = (GameMessage.HostGameRequest) message;
        assertThat(request.mapName(), nullValue());
        assertThat(request.featuredMod(), nullValue());
        assertThat(request.password(), nullValue());
        assertThat(request.ratingMin(), nullValue());
        assertThat(request.ratingMax(), nullValue());
        assertThat(request.access(), is(GameAccess.PUBLIC));
        assertThat(request.visibility(), is(GameVisibility.PUBLIC));
    }

    @Test
    void deserializeJoinGameRequest() {
        GameMessage.Client message = deserializeClient("""
                {"command": "game_join", "request_id": "req-3", "uid": 42, "password": "secret"}
                """);

        assertThat(message, is(new GameMessage.JoinGameRequest("req-3", 42, "secret")));
        assertThat(message.command(), is(GameMessage.JoinGameRequest.COMMAND));
    }

    @Test
    void deserializeJoinGameRequestWithoutPassword() {
        GameMessage.Client message = deserializeClient("""
                {"command": "game_join", "request_id": "req-4", "uid": 42}
                """);

        assertThat(message, is(new GameMessage.JoinGameRequest("req-4", 42, null)));
    }

    @Test
    void deserializeRestoreGameSessionRequest() {
        GameMessage.Client message = deserializeClient("""
                {"command": "restore_game_session", "request_id": "req-5", "game_id": 42}
                """);

        assertThat(message, is(new GameMessage.RestoreGameSessionRequest("req-5", 42)));
        assertThat(message.command(), is(GameMessage.RestoreGameSessionRequest.COMMAND));
    }

    @Test
    void clientMessagesRoundTrip() {
        List<GameMessage.Client> messages = List.of(
                new GameMessage.JoinGameRequest("req-6", 1, "pw"),
                new GameMessage.RestoreGameSessionRequest("req-7", 2));

        for (GameMessage.Client message : messages) {
            String json = JSON_MAPPER.writerFor(GameMessage.Client.class).writeValueAsString(message);
            assertThat(deserializeClient(json), is(message));
        }
    }

    // ---- Faction wire formats ----

    @Test
    void factionDeserializesFromNameAndIndex() {
        assertThat(JSON_MAPPER.readValue("\"seraphim\"", Faction.class), is(Faction.SERAPHIM));
        assertThat(JSON_MAPPER.readValue("4", Faction.class), is(Faction.SERAPHIM));
    }
}
