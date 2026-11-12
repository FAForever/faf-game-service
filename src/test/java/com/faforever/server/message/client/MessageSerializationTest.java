package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.Faction;
import com.faforever.server.message.client.dto.GameAccess;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.client.dto.GameStatus;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.client.dto.JsonStatsPayload;
import com.faforever.server.message.client.dto.LobbyMode;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class MessageSerializationTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
                                                            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                                                            .build();

    private static String serializeServer(GameMessage.Server message) {
        return JSON_MAPPER.writerFor(GameMessage.Server.class).writeValueAsString(message);
    }

    private static String serializeGPGServer(GPGMessage.Wrapped<GPGMessage.Server> message) {
        return JSON_MAPPER.writerFor(GameMessage.Server.class).writeValueAsString(message);
    }

    private static GameMessage.Client deserializeClient(String json) {
        return JSON_MAPPER.readValue(json, GameMessage.Client.class);
    }

    private static GPGMessage.Wrapped<GPGMessage.Client> deserializeGPGClient(String json) {
        return JSON_MAPPER.readValue(json, new TypeReference<>() {});
    }

    private static GPGMessage.Client deserializeGPGClientContent(String json) {
        return deserializeGPGClient(json).content();
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
                                """, json, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void serializeGameLaunchResponse() throws JSONException {
        String json = serializeServer(new GameMessage.GameLaunchResponse(7, "Test Game", "faf",
                                                                         LobbyMode.AUTO_LOBBY, GameType.MATCHMAKER,
                                                                         "ladder_1v1", "scmp_007", 2, 2,
                                                                         Map.of("Share", "ShareUntilDeath"), 1,
                                                                         Faction.CYBRAN));

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
                                """, json, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void serializeGameLaunchResponseWithNulls() throws JSONException {
        String json = serializeServer(new GameMessage.GameLaunchResponse(7, "Test Game", "faf",
                                                                         LobbyMode.DEFAULT_LOBBY, GameType.CUSTOM,
                                                                         "global", "scmp_007", null, null, Map.of(),
                                                                         null, null));

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
                                """, json, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void serializeGameInfoList() throws JSONException {
        GameInfo gameInfo = new GameInfo(5, "Test Game", "host", GameType.CUSTOM, 8, 3, GameVisibility.PRIVATE,
                                         true, GameStatus.OPEN, "faf", "global", Map.of("uid-1", "Some Mod"),
                                         "scmp_007",
                                         "maps/scmp_007.zip",
                                         OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC).toInstant(),
                                         1767323045L,
                                         "global", 800, 1200, true, Map.of(), Set.of());

        String json = serializeServer(new GameMessage.GameInfoList(List.of(gameInfo)));

        JSONAssert.assertEquals("""
                                {
                                  "command": "game_info",
                                  "games": [
                                    {
                                      "command": "game_info",
                                      "uid": 5,
                                      "title": "Test Game",
                                      "host": "host",
                                      "game_type": "custom",
                                      "max_players": 8,
                                      "num_players": 3,
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
                                      "team_ids": [],
                                      "teams": {},
                                      "enforce_rating_range": true
                                    }
                                  ]
                                }
                                """, json, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void serializeEmptyGameInfoList() throws JSONException {
        String json = serializeServer(new GameMessage.GameInfoList(List.of()));

        JSONAssert.assertEquals("""
                                {"command": "game_info", "games": []}
                                """, json, JSONCompareMode.NON_EXTENSIBLE);
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

    // ---- GPG client messages, per faf-re docs/GPGNET_MESSAGES.md section 3 ----

    @Test
    void deserializeGPGGameState() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameState", "target": "game", "args": ["Idle"]}
                                               """),
                   is(new GPGMessage.GameState(GPGMessage.GameState.State.Known.IDLE)));
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameState", "target": "game", "args": ["Lobby"]}
                                               """),
                   is(new GPGMessage.GameState(GPGMessage.GameState.State.Known.LOBBY)));

        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "GameState", "target": "game", "args": ["Launching"]}
                                                                """);
        assertThat(message, is(new GPGMessage.GameState(GPGMessage.GameState.State.Known.LAUNCHING)));
        assertThat(message.command(), is(GPGMessage.GameState.COMMAND));
    }

    @Test
    void deserializeGPGGameStateUnknownValue() {
        // The lobby server also accepts "Ended", which the game never sends
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameState", "target": "game", "args": ["Ended"]}
                                               """),
                   is(new GPGMessage.GameState(new GPGMessage.GameState.State.Unknown("Ended"))));
    }

    @Test
    void deserializeGPGGameOptionWithStringValue() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "GameOption", "target": "game", "args": ["Victory", "demoralization"]}
                                                                """);

        assertThat(message,
                   is(new GPGMessage.GameOption("Victory", new GPGMessage.OptionValue.Str("demoralization"))));
        assertThat(message.command(), is(GPGMessage.GameOption.COMMAND));
        assertThat(message, instanceOf(GPGMessage.Client.HostOnly.class));
    }

    @Test
    void deserializeGPGGameOptionScenarioFile() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameOption", "target": "game", "args": ["ScenarioFile", "/maps/scmp_009/scmp_009_scenario.lua"]}
                                               """),
                   is(new GPGMessage.GameOption("ScenarioFile",
                                                new GPGMessage.OptionValue.Str(
                                                        "/maps/scmp_009/scmp_009_scenario.lua"))));
    }

    @Test
    void deserializeGPGGameOptionWithIntValue() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameOption", "target": "game", "args": ["Slots", 8]}
                                               """),
                   is(new GPGMessage.GameOption("Slots", new GPGMessage.OptionValue.Int(8))));
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameOption", "target": "game", "args": ["RestrictedCategories", 0]}
                                               """),
                   is(new GPGMessage.GameOption("RestrictedCategories", new GPGMessage.OptionValue.Int(0))));
    }

    @Test
    void deserializeGPGPlayerOption() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "PlayerOption", "target": "game", "args": ["123456", "Faction", 2]}
                                                                """);

        assertThat(message, is(new GPGMessage.PlayerOption(123456, "Faction", new GPGMessage.OptionValue.Int(2))));
        assertThat(message.command(), is(GPGMessage.PlayerOption.COMMAND));
        assertThat(message, instanceOf(GPGMessage.Client.HostOnly.class));
    }

    @Test
    void deserializeGPGPlayerOptionObserverStartSpot() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "PlayerOption", "target": "game", "args": ["123456", "StartSpot", -1]}
                                               """),
                   is(new GPGMessage.PlayerOption(123456, "StartSpot", new GPGMessage.OptionValue.Int(-1))));
    }

    @Test
    void deserializeGPGPlayerOptionWithStringValue() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "PlayerOption", "target": "game", "args": ["123456", "PlayerColor", "ff0000"]}
                                               """),
                   is(new GPGMessage.PlayerOption(123456, "PlayerColor",
                                                  new GPGMessage.OptionValue.Str("ff0000"))));
    }

    @Test
    void deserializeGPGAIOption() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "AIOption", "target": "game", "args": ["AI: Adaptive", "Team", 3]}
                                                                """);

        assertThat(message,
                   is(new GPGMessage.AIOption("AI: Adaptive", "Team", new GPGMessage.OptionValue.Int(3))));
        assertThat(message.command(), is(GPGMessage.AIOption.COMMAND));
        assertThat(message, instanceOf(GPGMessage.Client.HostOnly.class));
    }

    @Test
    void deserializeGPGClearSlot() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "ClearSlot", "target": "game", "args": [3]}
                                                                """);

        assertThat(message, is(new GPGMessage.ClearSlot(3)));
        assertThat(message.command(), is(GPGMessage.ClearSlot.COMMAND));
        assertThat(message, instanceOf(GPGMessage.Client.HostOnly.class));
    }

    @Test
    void deserializeGPGGameModsActivated() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "GameMods", "target": "game", "args": ["activated", 1]}
                                                                """);

        assertThat(message, is(new GPGMessage.GameMods.Activated(1)));
        assertThat(message.command(), is(GPGMessage.GameMods.COMMAND));
        assertThat(message, instanceOf(GPGMessage.Client.HostOnly.class));
    }

    @Test
    void deserializeGPGGameModsUids() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameMods", "target": "game", "args": ["uids", "e7846e9b-23a4-4b95-ae3a-fb69b289a585"]}
                                               """),
                   is(new GPGMessage.GameMods.Uids(List.of("e7846e9b-23a4-4b95-ae3a-fb69b289a585"))));
    }

    @Test
    void deserializeGPGGameModsMultipleUidsAreSpaceSeparated() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameMods", "target": "game", "args": ["uids", "uid-a uid-b uid-c"]}
                                               """),
                   is(new GPGMessage.GameMods.Uids(List.of("uid-a", "uid-b", "uid-c"))));
    }

    @Test
    void deserializeGPGGameFull() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "GameFull", "target": "game", "args": []}
                                                                """);

        assertThat(message, is(new GPGMessage.GameFull()));
        assertThat(message.command(), is(GPGMessage.GameFull.COMMAND));
    }

    @Test
    void deserializeGPGChat() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "Chat", "target": "game", "args": ["gl hf"]}
                                                                """);

        assertThat(message, is(new GPGMessage.Chat("gl hf")));
        assertThat(message.command(), is(GPGMessage.Chat.COMMAND));
    }

    @Test
    void deserializeGPGDisconnected() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "Disconnected", "target": "game", "args": ["123456"]}
                                                                """);

        assertThat(message, is(new GPGMessage.Disconnected("123456")));
        assertThat(message.command(), is(GPGMessage.Disconnected.COMMAND));
    }

    @Test
    void deserializeGPGLaunchStatus() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "LaunchStatus", "target": "game", "args": ["Ready"]}
                                                                """);

        assertThat(message, is(new GPGMessage.LaunchStatus(GPGMessage.LaunchStatus.Status.Known.READY)));
        assertThat(message.command(), is(GPGMessage.LaunchStatus.COMMAND));
    }

    @Test
    void deserializeGPGLaunchStatusAllDocumentedValues() {
        Map<String, GPGMessage.LaunchStatus.Status> expected = Map.of(
                "Connecting", GPGMessage.LaunchStatus.Status.Known.CONNECTING,
                "Missing local peers", GPGMessage.LaunchStatus.Status.Known.MISSING_LOCAL_PEERS,
                "Rejoining", GPGMessage.LaunchStatus.Status.Known.REJOINING,
                "Ready", GPGMessage.LaunchStatus.Status.Known.READY,
                "Ejected", GPGMessage.LaunchStatus.Status.Known.EJECTED,
                "Rejected", GPGMessage.LaunchStatus.Status.Known.REJECTED);

        expected.forEach((wireValue, status) -> assertThat(deserializeGPGClientContent("""
                                                                                       {"command": "LaunchStatus", "target": "game", "args": ["%s"]}
                                                                                       """.formatted(wireValue)),
                                                           is(new GPGMessage.LaunchStatus(status))));
    }

    @Test
    void deserializeGPGLaunchStatusUnknownValue() {
        // "Failed" is in the documented enum but has no Known constant
        assertThat(deserializeGPGClientContent("""
                                               {"command": "LaunchStatus", "target": "game", "args": ["Failed"]}
                                               """),
                   is(new GPGMessage.LaunchStatus(new GPGMessage.LaunchStatus.Status.Unknown("Failed"))));
    }

    @Test
    void deserializeGPGEstablishedPeer() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "EstablishedPeer", "target": "game", "args": ["123456"]}
                                                                """);

        assertThat(message, is(new GPGMessage.EstablishedPeer("123456")));
        assertThat(message.command(), is(GPGMessage.EstablishedPeer.COMMAND));
    }

    @Test
    void deserializeGPGDisconnectedPeer() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "DisconnectedPeer", "target": "game", "args": ["123456"]}
                                                                """);

        assertThat(message, is(new GPGMessage.DisconnectedPeer("123456")));
        assertThat(message.command(), is(GPGMessage.DisconnectedPeer.COMMAND));
    }

    @Test
    void deserializeGPGGameResult() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "GameResult", "target": "game", "args": [1, "victory 10"]}
                                                                """);

        assertThat(message, is(new GPGMessage.GameResult(1, "victory 10")));
        assertThat(message.command(), is(GPGMessage.GameResult.COMMAND));
    }

    /**
     * Wraps a JsonStats payload document in the GPG envelope, where it travels as a single string arg.
     */
    private static GPGMessage.Client deserializeGPGJsonStatsContent(String payloadJson) {
        return deserializeGPGClientContent(JSON_MAPPER.writeValueAsString(
                Map.of("command", "JsonStats", "target", "game", "args", List.of(payloadJson))));
    }

    @Test
    void deserializeGPGJsonStats() {
        // The example from the doc's JsonStats schema
        GPGMessage.Client message = deserializeGPGJsonStatsContent("""
                {"stats":[{"name":"Player1","type":"Human","faction":1,"general":{"score":5370,"lastupdatetick":18240,"currentunits":87,"currentcap":1000,"kills":{"count":11,"mass":18483.28,"energy":5002463},"built":{"count":19,"mass":532,"energy":4160},"lost":{"count":1,"mass":18000,"energy":5000000}},"units":{"land":{"kills":3,"built":12,"lost":1},"cdr":{"kills":1,"built":0,"lost":0},"sacu":{"kills":0,"built":0,"lost":0}},"blueprints":{"uel0001":{"kills":1,"built":0,"lost":0,"lowest_health":4210.5}},"resources":{"massin":{"total":1004.2,"rate":0.8,"reclaimed":120,"reclaimRate":0},"massout":{"total":950,"rate":0.7,"excess":12},"energyin":{"total":10129.7,"rate":4,"reclaimed":0,"reclaimRate":0},"energyout":{"total":9800,"rate":3.9,"excess":300},"storage":{"storedMass":54,"storedEnergy":330,"maxMass":650,"maxEnergy":4000}}}]}
                """);

        JsonStatsPayload.ArmyStats expectedArmy = new JsonStatsPayload.ArmyStats(
                "Player1",
                JsonStatsPayload.BrainType.Known.HUMAN,
                Faction.UEF,
                null,
                new JsonStatsPayload.General(5370, 18240, 87, 1000,
                                                 new JsonStatsPayload.CountMassEnergy(11, 18483.28, 5002463.0),
                                                 new JsonStatsPayload.CountMassEnergy(19, 532.0, 4160.0),
                                                 new JsonStatsPayload.CountMassEnergy(1, 18000.0, 5000000.0)),
                Map.of("land", new JsonStatsPayload.KillsBuiltLost(3, 12, 1),
                       "cdr", new JsonStatsPayload.KillsBuiltLost(1, 0, 0),
                       "sacu", new JsonStatsPayload.KillsBuiltLost(0, 0, 0)),
                Map.of("uel0001", new JsonStatsPayload.BlueprintStats(1, 0, 0, 4210.5)),
                new JsonStatsPayload.Resources(
                        new JsonStatsPayload.ResourceIn(1004.2, 0.8, 120.0, 0.0),
                        new JsonStatsPayload.ResourceIn(10129.7, 4.0, 0.0, 0.0),
                        new JsonStatsPayload.ResourceOut(950.0, 0.7, 12.0),
                        new JsonStatsPayload.ResourceOut(9800.0, 3.9, 300.0),
                        new JsonStatsPayload.Storage(54.0, 330.0, 650.0, 4000.0)));

        assertThat(message, is(new GPGMessage.JsonStats(new JsonStatsPayload(List.of(expectedArmy)))));
        assertThat(message.command(), is(GPGMessage.JsonStats.COMMAND));
    }

    @Test
    void deserializeGPGJsonStatsWithCivilianGapDefeatedAndAi() {
        // A civilian army between two players leaves a null; Defeated appears once an army is defeated;
        // blueprints is optional
        GPGMessage.Client message = deserializeGPGJsonStatsContent("""
                {"stats":[
                  {"name":"Player1","type":"Human","faction":4,"Defeated":-1,"general":{"score":10},"units":{},"resources":{}},
                  null,
                  {"name":"AI: Adaptive","type":"AI","faction":3,"general":{"score":20},"units":{},"resources":{}}
                ]}
                """);

        assertThat(message, instanceOf(GPGMessage.JsonStats.class));
        List<JsonStatsPayload.ArmyStats> stats = ((GPGMessage.JsonStats) message).payload().stats();
        assertThat(stats.size(), is(3));
        assertThat(stats.get(0).faction(), is(Faction.SERAPHIM));
        assertThat(stats.get(0).defeated(), is(-1.0));
        assertThat(stats.get(0).blueprints(), nullValue());
        assertThat(stats.get(1), nullValue());
        assertThat(stats.get(2).type(), is(JsonStatsPayload.BrainType.Known.AI));
        assertThat(stats.get(2).faction(), is(Faction.CYBRAN));
        assertThat(stats.get(2).defeated(), nullValue());
    }

    @Test
    void deserializeGPGJsonStatsUnknownBrainType() {
        GPGMessage.Client message = deserializeGPGJsonStatsContent("""
                {"stats":[{"name":"Player1","type":"Spectator","faction":1,"general":{},"units":{},"resources":{}}]}
                """);

        assertThat(((GPGMessage.JsonStats) message).payload().stats().getFirst().type(),
                   is(new JsonStatsPayload.BrainType.Unknown("Spectator")));
    }

    @Test
    void deserializeGPGJsonStatsIgnoresUnknownFields() {
        // Fields the doc doesn't list (e.g. from a newer game build) must not invalidate the stats
        GPGMessage.Client message = deserializeGPGJsonStatsContent("""
                {"version":2,"stats":[{"name":"Player1","type":"Human","faction":1,"team":2,
                  "general":{"score":10,"apm":120,"kills":{"count":1,"mass":2,"energy":3,"bonus":4}},
                  "units":{"land":{"kills":1,"built":2,"lost":3,"damage":4}},
                  "blueprints":{"uel0001":{"kills":0,"built":0,"lost":0,"veterancy":5}},
                  "resources":{"massin":{"total":1,"peak":9},"storage":{"maxMass":650,"minMass":0},"reclaim":{}}}]}
                """);

        assertThat(message, instanceOf(GPGMessage.JsonStats.class));
        JsonStatsPayload.ArmyStats army = ((GPGMessage.JsonStats) message).payload().stats().getFirst();
        assertThat(army.general().score(), is(10));
        assertThat(army.general().kills(), is(new JsonStatsPayload.CountMassEnergy(1, 2.0, 3.0)));
        assertThat(army.units(), is(Map.of("land", new JsonStatsPayload.KillsBuiltLost(1, 2, 3))));
        assertThat(army.blueprints(),
                   is(Map.of("uel0001", new JsonStatsPayload.BlueprintStats(0, 0, 0, null))));
        assertThat(army.resources().massIn(), is(new JsonStatsPayload.ResourceIn(1.0, null, null, null)));
        assertThat(army.resources().storage(),
                   is(new JsonStatsPayload.Storage(null, null, 650.0, null)));
    }

    @Test
    void deserializeGPGJsonStatsMalformedJsonAsInvalid() {
        assertThat(deserializeGPGJsonStatsContent("{\"stats\":["),
                   is(new GPGMessage.Invalid("JsonStats", List.of("{\"stats\":["))));
    }

    @Test
    void deserializeGPGJsonStatsWrongShapeAsInvalid() {
        assertThat(deserializeGPGJsonStatsContent("{\"stats\":\"none\"}"),
                   is(new GPGMessage.Invalid("JsonStats", List.of("{\"stats\":\"none\"}"))));
    }

    @Test
    void deserializeGPGJsonStatsNotAStringAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "JsonStats", "target": "game", "args": [5]}
                                               """),
                   is(new GPGMessage.Invalid("JsonStats", List.of(5))));
    }

    @Test
    void deserializeGPGEnforceRating() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "EnforceRating", "target": "game", "args": []}
                                                                """);

        assertThat(message, is(new GPGMessage.EnforceRating()));
        assertThat(message.command(), is(GPGMessage.EnforceRating.COMMAND));
    }

    @Test
    void deserializeGPGGameEnded() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "GameEnded", "target": "game", "args": []}
                                                                """);

        assertThat(message, is(new GPGMessage.GameEnded()));
        assertThat(message.command(), is(GPGMessage.GameEnded.COMMAND));
    }

    @Test
    void deserializeGPGTeamkillHappened() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "TeamkillHappened", "target": "game", "args": [412, "123456", "Victim", "654321", "Killer"]}
                                                                """);

        assertThat(message, is(new GPGMessage.TeamkillHappened(412, "123456", "Victim", "654321", "Killer")));
        assertThat(message.command(), is(GPGMessage.TeamkillHappened.COMMAND));
    }

    @Test
    void deserializeGPGTeamkillHappenedWithIntZeroIdForAi() {
        // An id is the int 0 (not text) when the army has no client
        assertThat(deserializeGPGClientContent("""
                                               {"command": "TeamkillHappened", "target": "game", "args": [412, 0, "-", "654321", "Killer"]}
                                               """),
                   is(new GPGMessage.TeamkillHappened(412, "0", "-", "654321", "Killer")));
    }

    @Test
    void deserializeGPGTeamkillReport() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "TeamkillReport", "target": "game", "args": [412, "123456", "Victim", "654321", "Killer"]}
                                                                """);

        assertThat(message, is(new GPGMessage.TeamkillReport(412, "123456", "Victim", "654321", "Killer")));
        assertThat(message.command(), is(GPGMessage.TeamkillReport.COMMAND));
    }

    @Test
    void deserializeGPGOperationComplete() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "OperationComplete", "target": "game", "args": [1, 0, "00:42:17"]}
                                                                """);

        assertThat(message, is(new GPGMessage.OperationComplete(true, false, "00:42:17")));
        assertThat(message.command(), is(GPGMessage.OperationComplete.COMMAND));
    }

    @Test
    void deserializeGPGRehost() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "Rehost", "target": "game", "args": []}
                                                                """);

        assertThat(message, is(new GPGMessage.Rehost()));
        assertThat(message.command(), is(GPGMessage.Rehost.COMMAND));
    }

    @Test
    void deserializeGPGBeat() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "BEAT", "target": "game", "args": [6000, 0]}
                                                                """);

        assertThat(message, is(new GPGMessage.Beat(6000, 0)));
        assertThat(message.command(), is(GPGMessage.Beat.COMMAND));
    }

    @Test
    void deserializeGPGBottleneckDocExample() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "Bottleneck", "target": "game", "args": ["data", "19508", "517268,516974,344419", "5980.1"]}
                                                                """);

        assertThat(message, is(new GPGMessage.Bottleneck(GPGMessage.Bottleneck.Kind.Known.DATA, 19508,
                                                         List.of(517268, 516974, 344419), 5980.1)));
    }

    @Test
    void deserializeGPGBottleneckAllKnownKinds() {
        Map<String, GPGMessage.Bottleneck.Kind> expected = Map.of(
                "nothing", GPGMessage.Bottleneck.Kind.Known.NOTHING,
                "readiness", GPGMessage.Bottleneck.Kind.Known.READINESS,
                "data", GPGMessage.Bottleneck.Kind.Known.DATA,
                "ack", GPGMessage.Bottleneck.Kind.Known.ACK);

        expected.forEach((wireValue, kind) -> assertThat(deserializeGPGClientContent("""
                                                                                     {"command": "Bottleneck", "target": "game", "args": ["%s", "1", "2", "3.0"]}
                                                                                     """.formatted(wireValue)),
                                                         is(new GPGMessage.Bottleneck(kind, 1, List.of(2), 3.0))));
    }

    @Test
    void deserializeGPGBottleneckUnknownKindAndNoIds() {
        // "unknown" is a documented kind with no Known constant; the id list may be empty
        assertThat(deserializeGPGClientContent("""
                                               {"command": "Bottleneck", "target": "game", "args": ["unknown", "19508", "", "0.0"]}
                                               """),
                   is(new GPGMessage.Bottleneck(new GPGMessage.Bottleneck.Kind.Unknown("unknown"), 19508,
                                                List.of(), 0.0)));
    }

    @Test
    void deserializeGPGBottleneckCleared() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "BottleneckCleared", "target": "game", "args": []}
                                                                """);

        assertThat(message, is(new GPGMessage.BottleneckCleared()));
        assertThat(message.command(), is(GPGMessage.BottleneckCleared.COMMAND));
    }

    @Test
    void deserializeGPGDesync() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "Desync", "target": "game", "args": [1234, 1, "9e107d9d372bb6826bd81d3542a419d6", "e4d909c290d0fb1ca068ffaddf22cbd0"]}
                                                                """);

        assertThat(message, is(new GPGMessage.Desync(1234, 1, "9e107d9d372bb6826bd81d3542a419d6",
                                                     "e4d909c290d0fb1ca068ffaddf22cbd0")));
        assertThat(message.command(), is(GPGMessage.Desync.COMMAND));
    }

    @Test
    void deserializeGPGStats() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "Stats", "target": "game", "args": ["<?xml version=\\"1.0\\"?><GameStats></GameStats>"]}
                                                                """);

        assertThat(message, is(new GPGMessage.XMLStats("<?xml version=\"1.0\"?><GameStats></GameStats>")));
        assertThat(message.command(), is("Stats"));
    }

    @Test
    void deserializeGPGProcessNatPacket() {
        // Data arguments (wire tag 2) are base64 in JSON
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "ProcessNatPacket", "target": "game", "args": ["192.168.0.1:6112", "AQID"]}
                                                                """);

        assertThat(message, instanceOf(GPGMessage.ProcessNatPacket.class));
        GPGMessage.ProcessNatPacket packet = (GPGMessage.ProcessNatPacket) message;
        assertThat(packet.fromIpPort(), is("192.168.0.1:6112"));
        assertThat(packet.data(), equalTo(new byte[]{1, 2, 3}));
        assertThat(packet.command(), is(GPGMessage.ProcessNatPacket.COMMAND));
    }

    @Test
    void deserializeGPGUnrecognizedCommandAsUnknown() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "RejoinOffer", "target": "game", "args": ["123456", 7]}
                                                                """);

        assertThat(message, is(new GPGMessage.Unknown("RejoinOffer", List.of("123456", 7))));
        assertThat(message.command(), is("RejoinOffer"));
    }

    @Test
    void deserializeGPGMessageWithoutTarget() {
        // target is optional in the Envelope schema
        assertThat(deserializeGPGClientContent("""
                                               {"command": "Chat", "args": ["gl hf"]}
                                               """),
                   is(new GPGMessage.Chat("gl hf")));
    }

    // ---- GPG Unknown: commands with no registered subtype ----

    @Test
    void deserializeGPGUnrecognizedCommandWithoutArgsAsUnknown() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "SomethingNew", "target": "game", "args": []}
                                                                """);

        assertThat(message, is(new GPGMessage.Unknown("SomethingNew", List.of())));
        assertThat(message.command(), is("SomethingNew"));
    }

    @Test
    void deserializeGPGToGameCommandSentByClientAsUnknown() {
        // Messages to the game (doc section 5) are not valid client messages
        assertThat(deserializeGPGClientContent("""
                                               {"command": "CreateLobby", "target": "game", "args": [0, 6112, "Player1", 123456, 1]}
                                               """),
                   is(new GPGMessage.Unknown("CreateLobby", List.of(0, 6112, "Player1", 123456, 1))));
    }

    @Test
    void deserializeGPGCommandNameIsCaseSensitive() {
        // The game sends "BEAT"; any other casing is not a known command
        assertThat(deserializeGPGClientContent("""
                                               {"command": "beat", "target": "game", "args": [6000, 0]}
                                               """),
                   is(new GPGMessage.Unknown("beat", List.of(6000, 0))));
    }

    // ---- GPG Invalid: known commands whose args do not fit ----

    @Test
    void deserializeGPGNonNumericStringForIntArgAsInvalid() {
        GPGMessage.Client message = deserializeGPGClientContent("""
                                                                {"command": "Bottleneck", "target": "game", "args": ["nothing", "a", "5,6,7", "6000.1"]}
                                                                """);

        assertThat(message, is(new GPGMessage.Invalid("Bottleneck", List.of("nothing", "a", "5,6,7", "6000.1"))));
        assertThat(message.command(), is("Bottleneck"));
    }

    @Test
    void deserializeGPGNonNumericIdListAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "Bottleneck", "target": "game", "args": ["data", "1", "5,x", "1.0"]}
                                               """),
                   is(new GPGMessage.Invalid("Bottleneck", List.of("data", "1", "5,x", "1.0"))));
    }

    @Test
    void deserializeGPGStringForIntArgAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "ClearSlot", "target": "game", "args": ["three"]}
                                               """),
                   is(new GPGMessage.Invalid("ClearSlot", List.of("three"))));
    }

    @Test
    void deserializeGPGNonNumericPlayerOptionOwnerIdAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "PlayerOption", "target": "game", "args": ["not-a-uid", "Faction", 2]}
                                               """),
                   is(new GPGMessage.Invalid("PlayerOption", List.of("not-a-uid", "Faction", 2))));
    }

    @Test
    void deserializeGPGDesyncWithStringBeatAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "Desync", "target": "game", "args": ["beat", 1, "aa", "bb"]}
                                               """),
                   is(new GPGMessage.Invalid("Desync", List.of("beat", 1, "aa", "bb"))));
    }

    @Test
    void deserializeGPGGameModsUnknownTypeAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameMods", "target": "game", "args": ["something", 1]}
                                               """),
                   is(new GPGMessage.Invalid("GameMods", List.of("something", 1))));
    }

    @Test
    void deserializeGPGGameModsMismatchedArgTypeAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameMods", "target": "game", "args": ["uids", 3]}
                                               """),
                   is(new GPGMessage.Invalid("GameMods", List.of("uids", 3))));
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameMods", "target": "game", "args": ["activated", "3"]}
                                               """),
                   is(new GPGMessage.Invalid("GameMods", List.of("activated", "3"))));
    }

    @Test
    void deserializeGPGTooFewArgsAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameResult", "target": "game", "args": [1]}
                                               """),
                   is(new GPGMessage.Invalid("GameResult", List.of(1))));
    }

    @Test
    void deserializeGPGTooManyArgsAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "Chat", "target": "game", "args": ["gl hf", "extra"]}
                                               """),
                   is(new GPGMessage.Invalid("Chat", List.of("gl hf", "extra"))));
    }

    @Test
    void deserializeGPGGameModsTooManyArgsAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameMods", "target": "game", "args": ["activated", 1, 2]}
                                               """),
                   is(new GPGMessage.Invalid("GameMods", List.of("activated", 1, 2))));
    }

    @Test
    void deserializeGPGGameModsTooFewArgsAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameMods", "target": "game", "args": ["activated"]}
                                               """),
                   is(new GPGMessage.Invalid("GameMods", List.of("activated"))));
    }

    @Test
    void deserializeGPGArgsForNoArgCommandAsInvalid() {
        assertThat(deserializeGPGClientContent("""
                                               {"command": "GameEnded", "target": "game", "args": [1]}
                                               """),
                   is(new GPGMessage.Invalid("GameEnded", List.of(1))));
    }

    // ---- Faction wire formats ----

    @Test
    void factionDeserializesFromNameAndIndex() {
        assertThat(JSON_MAPPER.readValue("\"seraphim\"", Faction.class), is(Faction.SERAPHIM));
        assertThat(JSON_MAPPER.readValue("4", Faction.class), is(Faction.SERAPHIM));
    }
}
