package com.faforever.server.message.client;

import com.faforever.server.jackson.JacksonCustomizer;
import com.faforever.server.message.client.dto.Faction;
import com.faforever.server.message.client.dto.GameAccess;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.client.dto.GameStatus;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.client.dto.JsonStatsPayload;
import com.faforever.server.message.client.dto.LobbyMode;
import com.faforever.server.message.client.dto.OptionValue;
import com.faforever.server.message.client.dto.ResultDetails;
import com.faforever.server.message.client.dto.SlotOption;
import io.quarkus.jackson.runtime.JsonMapperProducer;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusComponentTest({JsonMapperProducer.class, JacksonCustomizer.class})
class MessageSerializationTest {

    @Inject
    JsonMapper jsonMapper;

    private String serializeGameServer(GameMessage.Server message) {
        return jsonMapper.writeValueAsString(message);
    }

    private GameMessage.Client deserializeGameClient(String json) {
        return jsonMapper.readValue(json, GameMessage.Client.class);
    }

    private String serializeGPGServer(GPGMessage.Server message) {
        return jsonMapper.writeValueAsString(new GPGMessage.Wrapped<>(message));
    }

    private GPGMessage.Client deserializeGPGClient(String json) {
        return jsonMapper.<GPGMessage.Wrapped<GPGMessage.Client>>readValue(json, new TypeReference<>() {}).content();
    }

    // ---- Server messages ----

    @Test
    void serializeGameJoinFailed() throws JSONException {
        String json = serializeGameServer(new GameMessage.GameJoinFailed("Game is full", 42));

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
        String json = serializeGameServer(
                new GameMessage.GameLaunchResponse(7, "Test Game", "faf", LobbyMode.AUTO_LOBBY, GameType.MATCHMAKER,
                                                   "ladder_1v1", "scmp_007", 2, 2, Map.of("Share", "ShareUntilDeath"),
                                                   1, Faction.CYBRAN));

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
        String json = serializeGameServer(
                new GameMessage.GameLaunchResponse(7, "Test Game", "faf", LobbyMode.DEFAULT_LOBBY, GameType.CUSTOM,
                                                   "global", "scmp_007", null, null, Map.of(), null, null));

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
        GameInfo gameInfo = new GameInfo(5, "Test Game", "host", GameType.CUSTOM, 8, 3, GameVisibility.PRIVATE, true,
                                         GameStatus.OPEN, "faf", "global", Map.of("uid-1", "Some Mod"), "scmp_007",
                                         "maps/scmp_007.zip",
                                         OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC).toInstant(),
                                         1767323045L, "global", 800, 1200, true, Map.of(1, Set.of("test")), Set.of(new GameInfo.Team(1, Set.of(2))));

        String json = serializeGameServer(new GameMessage.GameInfoList(List.of(gameInfo)));

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
                                      "mapname": "scmp_007",
                                      "map_file_path": "maps/scmp_007.zip",
                                      "hosted_at": "2026-01-02T03:04:05Z",
                                      "launched_at": 1767323045,
                                      "rating_min": 800,
                                      "rating_max": 1200,
                                      "teams_ids": [{"team_id": 1, "player_ids": [2]}],
                                      "teams": {
                                          "1": ["test"]
                                      },
                                      "enforce_rating_range": true
                                    }
                                  ]
                                }
                                """, json, JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void serializeEmptyGameInfoList() throws JSONException {
        String json = serializeGameServer(new GameMessage.GameInfoList(List.of()));

        JSONAssert.assertEquals("""
                                {"command": "game_info", "games": []}
                                """, json, JSONCompareMode.NON_EXTENSIBLE);
    }

    // ---- Client messages ----

    @Test
    void deserializeHostGameRequest() {
        GameMessage.Client message = deserializeGameClient("""
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
        GameMessage.Client message = deserializeGameClient("""
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
        GameMessage.Client message = deserializeGameClient("""
                                                           {"command": "game_join", "request_id": "req-3", "uid": 42, "password": "secret"}
                                                           """);

        assertThat(message, is(new GameMessage.JoinGameRequest("req-3", 42, "secret")));
        assertThat(message.command(), is(GameMessage.JoinGameRequest.COMMAND));
    }

    @Test
    void deserializeJoinGameRequestWithoutPassword() {
        GameMessage.Client message = deserializeGameClient("""
                                                           {"command": "game_join", "request_id": "req-4", "uid": 42}
                                                           """);

        assertThat(message, is(new GameMessage.JoinGameRequest("req-4", 42, null)));
    }

    @Test
    void deserializeRestoreGameSessionRequest() {
        GameMessage.Client message = deserializeGameClient("""
                                                           {"command": "restore_game_session", "request_id": "req-5", "game_id": 42}
                                                           """);

        assertThat(message, is(new GameMessage.RestoreGameSessionRequest("req-5", 42)));
        assertThat(message.command(), is(GameMessage.RestoreGameSessionRequest.COMMAND));
    }

    @Test
    void clientMessagesRoundTrip() {
        List<GameMessage.Client> messages = List.of(new GameMessage.JoinGameRequest("req-6", 1, "pw"),
                                                    new GameMessage.RestoreGameSessionRequest("req-7", 2));

        for (GameMessage.Client message : messages) {
            String json = jsonMapper.writerFor(GameMessage.Client.class).writeValueAsString(message);
            assertThat(deserializeGameClient(json), is(message));
        }
    }

    // ---- GPG client messages, per faf-re docs/GPGNET_MESSAGES.md section 3 ----

    @Test
    void deserializeGPGGameState() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameState", "target": "game", "args": ["Idle"]}
                                        """), is(new GPGMessage.GameState(GPGMessage.GameState.State.Known.IDLE)));
        assertThat(deserializeGPGClient("""
                                        {"command": "GameState", "target": "game", "args": ["Lobby"]}
                                        """), is(new GPGMessage.GameState(GPGMessage.GameState.State.Known.LOBBY)));

        assertThat(deserializeGPGClient("""
                                        {"command": "GameState", "target": "game", "args": ["Launching"]}
                                        """), is(new GPGMessage.GameState(GPGMessage.GameState.State.Known.LAUNCHING)));
        assertThat(deserializeGPGClient("""
                                        {"command": "GameState", "target": "game", "args": ["Ended"]}
                                        """), is(new GPGMessage.GameState(GPGMessage.GameState.State.Known.ENDED)));
    }

    @Test
    void deserializeGPGGameStateUnknownValue() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameState", "target": "game", "args": ["End"]}
                                        """),
                   is(new GPGMessage.GameState(new GPGMessage.GameState.State.Unknown("End"))));
    }

    @Test
    void deserializeGPGGameOptionWithStringValue() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameOption", "target": "game", "args": ["Victory", "demoralization"]}
                                        """), is(new GameOption.Victory(GameOption.Victory.Known.DEMORALIZATION)));
    }

    @Test
    void deserializeGPGGameOptionScenarioFile() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameOption", "target": "game", "args": ["ScenarioFile", "/maps/scmp_009/scmp_009_scenario.lua"]}
                                        """),
                   is(new GameOption.ScenarioFile(Path.of("/maps", "scmp_009", "scmp_009_scenario.lua"))));
    }

    @Test
    void deserializeGPGGameOptionScore() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameOption", "target": "game", "args": ["Score", "no"]}
                                        """), is(new GameOption.Other("Score", new OptionValue.Str("no"))));
    }

    @Test
    void deserializeGPGSlots() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameOption", "target": "game", "args": ["Slots", 8]}
                                        """), is(new GameOption.Slots(8)));
    }

    @Test
    void deserializeGPGRestrictedCategories() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameOption", "target": "game", "args": ["RestrictedCategories", 0]}
                                        """), is(new GameOption.RestrictedCategories(GameOption.TrueFalse.FALSE)));
    }

    @Test
    void deserializeGPGGameOptionWithIntValue() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameOption", "target": "game", "args": ["Sots", 8]}
                                        """), is(new GameOption.Other("Sots", new OptionValue.Int(8))));
        assertThat(deserializeGPGClient("""
                                        {"command": "GameOption", "target": "game", "args": ["Categories", 0]}
                                        """), is(new GameOption.Other("Categories", new OptionValue.Int(0))));
    }

    @Test
    void deserializeGPGPlayerOption() {

        assertThat(deserializeGPGClient("""
                                        {"command": "PlayerOption", "target": "game", "args": ["123456", "Faction", 2]}
                                        """), is(new GPGMessage.PlayerOption(123456, new SlotOption.Faction(2))));
    }

    @Test
    void deserializeGPGPlayerOptionObserverStartSpot() {
        assertThat(deserializeGPGClient("""
                                        {"command": "PlayerOption", "target": "game", "args": ["123456", "StartSpot", -1]}
                                        """), is(new GPGMessage.PlayerOption(123456, new SlotOption.Slot(-1))));
    }

    @Test
    void deserializeGPGPlayerOptionWithStringValue() {
        assertThat(deserializeGPGClient("""
                                        {"command": "PlayerOption", "target": "game", "args": ["123456", "PlayerColor", "1"]}
                                        """), is(new GPGMessage.PlayerOption(123456, new SlotOption.Color(1))));
    }

    @Test
    void deserializeGPGAIOption() {

        assertThat(deserializeGPGClient("""
                                        {"command": "AIOption", "target": "game", "args": ["AI: Adaptive", "Team", 3]}
                                        """), is(new GPGMessage.AIOption("AI: Adaptive", new SlotOption.Team(3))));
    }

    // ---- Game options, per faf-re docs/GPGNET_MESSAGES.md "GameOption" ----

    private GPGMessage.Client deserializeGameOption(String key, Object value) {
        return deserializeGPGClient(jsonMapper.writeValueAsString(
                Map.of("command", "GameOption", "target", "game", "args", List.of(key, value))));
    }

    @Test
    void deserializeGameOptionIsHostOnly() {
        assertThat(deserializeGameOption("Victory", "demoralization"), instanceOf(GPGMessage.Client.GameSetup.class));
        assertThat(deserializeGameOption("UnitCap", "1000"), instanceOf(GPGMessage.Client.GameSetup.class));
    }

    @Test
    void deserializeGameOptionVictoryKnownValues() {
        Map<String, GameOption.Victory.Known> expected = Map.of("demoralization",
                                                                GameOption.Victory.Known.DEMORALIZATION, "domination",
                                                                GameOption.Victory.Known.DOMINATION, "eradication",
                                                                GameOption.Victory.Known.ERADICATION, "sandbox",
                                                                GameOption.Victory.Known.SANDBOX);

        expected.forEach((value, known) -> assertThat(deserializeGameOption("Victory", value),
                                                      is(new GameOption.Victory(known))));
    }

    @Test
    void deserializeGameOptionVictoryIsCaseInsensitive() {
        // The server converts Victory case-insensitively; keys are matched case-insensitively too
        assertThat(deserializeGameOption("Victory", "Demoralization"),
                   is(new GameOption.Victory(GameOption.Victory.Known.DEMORALIZATION)));
        assertThat(deserializeGameOption("VICTORY", "domination"),
                   is(new GameOption.Victory(GameOption.Victory.Known.DOMINATION)));
    }

    @Test
    void deserializeGameOptionVictoryUnknownValue() {
        assertThat(deserializeGameOption("Victory", "kingofthehill"),
                   is(new GameOption.Victory(new GameOption.Unknown("kingofthehill"))));
    }

    @Test
    void deserializeGameOptionUnranked() {
        assertThat(deserializeGameOption("Unranked", "true"),
                   is(new GameOption.Unranked(GameOption.TrueFalse.TRUE)));
        assertThat(deserializeGameOption("Unranked", "false"),
                   is(new GameOption.Unranked(GameOption.TrueFalse.FALSE)));
        assertThat(deserializeGameOption("Unranked", "maybe"),
                   is(new GameOption.Unranked(new GameOption.Unknown("maybe"))));
    }

    @Test
    void deserializeGameOptionCheatsEnabled() {
        assertThat(deserializeGameOption("CheatsEnabled", "true"),
                   is(new GameOption.CheatsEnabled(GameOption.TrueFalse.TRUE)));
        assertThat(deserializeGameOption("CheatsEnabled", "false"),
                   is(new GameOption.CheatsEnabled(GameOption.TrueFalse.FALSE)));
        assertThat(deserializeGameOption("CheatsEnabled", "sometimes"),
                   is(new GameOption.CheatsEnabled(new GameOption.Unknown("sometimes"))));
    }

    /**
     * The server treats false, "false", "off", "no" and 0 as disabled, case-insensitively; the matching enabled
     * values are accepted as well. Values may arrive as strings or as ints (booleans are sent as 1/0).
     */
    private static final Map<Object, Boolean> TOGGLE_VALUES = Map.ofEntries(Map.entry("true", true),
                                                                            Map.entry("TRUE", true),
                                                                            Map.entry("on", true),
                                                                            Map.entry("On", true),
                                                                            Map.entry("yes", true),
                                                                            Map.entry("Yes", true),
                                                                            Map.entry("1", true), Map.entry(1, true),
                                                                            Map.entry("false", false),
                                                                            Map.entry("False", false),
                                                                            Map.entry("off", false),
                                                                            Map.entry("Off", false),
                                                                            Map.entry("no", false),
                                                                            Map.entry("No", false),
                                                                            Map.entry("0", false), Map.entry(0, false));

    @Test
    void deserializeGameOptionUnrankedAcceptsToggleValues() {
        TOGGLE_VALUES.forEach(
                (value, enabled) -> assertThat(String.valueOf(value), deserializeGameOption("Unranked", value),
                                               is(new GameOption.Unranked(enabled ?
                                                                          GameOption.TrueFalse.TRUE :
                                                                          GameOption.TrueFalse.FALSE))));
    }

    @Test
    void deserializeGameOptionCheatsEnabledAcceptsToggleValues() {
        TOGGLE_VALUES.forEach(
                (value, enabled) -> assertThat(String.valueOf(value), deserializeGameOption("CheatsEnabled", value),
                                               is(new GameOption.CheatsEnabled(enabled ?
                                                                               GameOption.TrueFalse.TRUE :
                                                                               GameOption.TrueFalse.FALSE))));
    }

    @Test
    void deserializeGameOptionToggleUnknownIntValue() {
        assertThat(deserializeGameOption("Unranked", 2), is(new GameOption.Unranked(new GameOption.Unknown("2"))));
        assertThat(deserializeGameOption("CheatsEnabled", 2),
                   is(new GameOption.CheatsEnabled(new GameOption.Unknown("2"))));
    }

    @Test
    void deserializeGameOptionFogOfWar() {
        assertThat(deserializeGameOption("FogOfWar", "explored"),
                   is(new GameOption.FogOfWar(GameOption.FogOfWar.Known.EXPLORED)));
        assertThat(deserializeGameOption("FogOfWar", "none"),
                   is(new GameOption.FogOfWar(GameOption.FogOfWar.Known.NONE)));
        assertThat(deserializeGameOption("FogOfWar", "partial"),
                   is(new GameOption.FogOfWar(new GameOption.Unknown("partial"))));
    }

    @Test
    void deserializeGameOptionTeamLock() {
        assertThat(deserializeGameOption("TeamLock", "locked"),
                   is(new GameOption.TeamLock(GameOption.TeamLock.Known.LOCKED)));
        assertThat(deserializeGameOption("TeamLock", "unlocked"),
                   is(new GameOption.TeamLock(GameOption.TeamLock.Known.UNLOCKED)));
        assertThat(deserializeGameOption("TeamLock", "half"),
                   is(new GameOption.TeamLock(new GameOption.Unknown("half"))));
    }

    @Test
    void deserializeGameOptionGameSpeed() {
        assertThat(deserializeGameOption("GameSpeed", "normal"),
                   is(new GameOption.GameSpeed(GameOption.GameSpeed.Known.NORMAL)));
        assertThat(deserializeGameOption("GameSpeed", "fast"),
                   is(new GameOption.GameSpeed(GameOption.GameSpeed.Known.FAST)));
        assertThat(deserializeGameOption("GameSpeed", "adjustable"),
                   is(new GameOption.GameSpeed(GameOption.GameSpeed.Known.ADJUSTABLE)));
        assertThat(deserializeGameOption("GameSpeed", "ludicrous"),
                   is(new GameOption.GameSpeed(new GameOption.Unknown("ludicrous"))));
    }

    @Test
    void deserializeGameOptionPrebuiltUnits() {
        assertThat(deserializeGameOption("PrebuiltUnits", "Off"),
                   is(new GameOption.PrebuiltUnits(GameOption.TrueFalse.FALSE)));
        assertThat(deserializeGameOption("PrebuiltUnits", "On"),
                   is(new GameOption.PrebuiltUnits(GameOption.TrueFalse.TRUE)));
        assertThat(deserializeGameOption("PrebuiltUnits", "Some"),
                   is(new GameOption.PrebuiltUnits(new GameOption.Unknown("Some"))));
    }

    @Test
    void deserializeGameOptionNoRushOption() {
        assertThat(deserializeGameOption("NoRushOption", 0), is(new GameOption.NoRushOption(0)));
        assertThat(deserializeGameOption("NoRushOption", "10"), is(new GameOption.NoRushOption(10)));
    }

    @Test
    void deserializeGameOptionNoRushOptionOffIsZero() {
        // "Off" means disabled, which the server treats the same as 0
        assertThat(deserializeGameOption("NoRushOption", "Off"), is(new GameOption.NoRushOption(0)));
        assertThat(deserializeGameOption("NoRushOption", "off"), is(new GameOption.NoRushOption(0)));
    }

    @Test
    void deserializeGameOptionNoRushOptionNonNumericAsInvalid() {
        assertThat(deserializeGameOption("NoRushOption", "soon"),
                   is(new GPGMessage.Invalid("GameOption", List.of("NoRushOption", "soon"))));
    }

    @Test
    void deserializeGameOptionShare() {
        Map<String, GameOption.Share.Known> expected = Map.of("FullShare", GameOption.Share.Known.FULL_SHARE,
                                                              "ShareUntilDeath",
                                                              GameOption.Share.Known.SHARE_UNTIL_DEATH, "PartialShare",
                                                              GameOption.Share.Known.PARTIAL_SHARE, "TransferToKiller",
                                                              GameOption.Share.Known.TRANSFER_TO_KILLER, "Defectors",
                                                              GameOption.Share.Known.DEFECTORS, "CivilianDesert",
                                                              GameOption.Share.Known.CIVILIAN_DESERT);

        expected.forEach(
                (value, known) -> assertThat(deserializeGameOption("Share", value), is(new GameOption.Share(known))));
        assertThat(deserializeGameOption("Share", "NoShare"),
                   is(new GameOption.Share(new GameOption.Unknown("NoShare"))));
    }

    @Test
    void deserializeAIReplacement() {
        assertThat(deserializeGameOption("AIReplacement", "Off"),
                   is(new GameOption.AIReplacement(GameOption.TrueFalse.FALSE)));
    }

    @Test
    void deserializeRestrictedCategories() {
        assertThat(deserializeGameOption("RestrictedCategories", 1),
                   is(new GameOption.RestrictedCategories(GameOption.TrueFalse.TRUE)));
    }

    @Test
    void deserializeGameOptionDocumentedKeysWithoutDedicatedTypeAsOther() {
        // Documented keys the service has no dedicated type for keep their raw key and value
        assertThat(deserializeGameOption("UnitCap", "1000"),
                   is(new GameOption.Other("UnitCap", new OptionValue.Str("1000"))));
    }

    @Test
    void deserializeGameOptionUnknownKeyAsOther() {
        assertThat(deserializeGameOption("SomeModOption", "enabled"),
                   is(new GameOption.Other("SomeModOption", new OptionValue.Str("enabled"))));
        assertThat(deserializeGameOption("SomeModOption", 3),
                   is(new GameOption.Other("SomeModOption", new OptionValue.Int(3))));
    }

    @Test
    void deserializeGameOptionWithUnsupportedValueTypeAsInvalid() {
        // Option values are only ever strings or ints on the wire
        assertThat(deserializeGameOption("UnitCap", 1.5),
                   is(new GPGMessage.Invalid("GameOption", List.of("UnitCap", 1.5))));
        assertThat(deserializeGameOption("UnitCap", true),
                   is(new GPGMessage.Invalid("GameOption", List.of("UnitCap", true))));
    }

    // ---- Slot options (PlayerOption / AIOption), per faf-re docs/GPGNET_MESSAGES.md "PlayerOption" ----

    private GPGMessage.Client deserializePlayerOption(String key, Object value) {
        return deserializeGPGClient(jsonMapper.writeValueAsString(
                Map.of("command", "PlayerOption", "target", "game", "args", List.of("123456", key, value))));
    }

    private GPGMessage.Client deserializeAIOption(String key, Object value) {
        return deserializeGPGClient(jsonMapper.writeValueAsString(
                Map.of("command", "AIOption", "target", "game", "args", List.of("AI: Adaptive", key, value))));
    }

    /**
     * Every documented slot key, with the value as the lobby sends it, and the option it should map to.
     */
    private static final Map<List<Object>, SlotOption> DOCUMENTED_SLOT_OPTIONS = Map.of(List.of("Faction", 2),
                                                                                        new SlotOption.Faction(2),
                                                                                        List.of("Faction", 5),
                                                                                        new SlotOption.Faction(
                                                                                                5), // slot-local index; the last means random
                                                                                        List.of("Color", 3),
                                                                                        new SlotOption.Color(3),
                                                                                        List.of("PlayerColor", 3),
                                                                                        new SlotOption.Color(3),
                                                                                        List.of("Team", 2),
                                                                                        new SlotOption.Team(2),
                                                                                        List.of("Team", -1),
                                                                                        new SlotOption.Team(
                                                                                                -1), // observers
                                                                                        List.of("StartSpot", 4),
                                                                                        new SlotOption.Slot(4),
                                                                                        List.of("StartSpot", -2),
                                                                                        new SlotOption.Slot(
                                                                                                -2), // observers: -(observer index)
                                                                                        List.of("Army", 1),
                                                                                        new SlotOption.Other("Army",
                                                                                                             new OptionValue.Int(
                                                                                                                     1)),
                                                                                        List.of("Ready", 1),
                                                                                        new SlotOption.Other("Ready",
                                                                                                             new OptionValue.Int(
                                                                                                                     1)));

    @Test
    void deserializePlayerOptionDocumentedKeys() {
        DOCUMENTED_SLOT_OPTIONS.forEach((keyValue, option) -> assertThat(keyValue.toString(), deserializePlayerOption(
                (String) keyValue.getFirst(), keyValue.get(1)), is(new GPGMessage.PlayerOption(123456, option))));
    }

    @Test
    void deserializeAIOptionDocumentedKeys() {
        DOCUMENTED_SLOT_OPTIONS.forEach((keyValue, option) -> assertThat(keyValue.toString(), deserializeAIOption(
                (String) keyValue.getFirst(), keyValue.get(1)), is(new GPGMessage.AIOption("AI: Adaptive", option))));
    }

    @Test
    void deserializeSlotOptionKeysAreCaseInsensitive() {
        assertThat(deserializePlayerOption("team", 2), is(new GPGMessage.PlayerOption(123456, new SlotOption.Team(2))));
        assertThat(deserializePlayerOption("STARTSPOT", 4),
                   is(new GPGMessage.PlayerOption(123456, new SlotOption.Slot(4))));
    }

    @Test
    void deserializeSlotOptionNumericStringValues() {
        // Slot fields are numbers or strings; numeric ones may arrive as text
        assertThat(deserializePlayerOption("Team", "2"),
                   is(new GPGMessage.PlayerOption(123456, new SlotOption.Team(2))));
        assertThat(deserializeAIOption("Faction", "3"),
                   is(new GPGMessage.AIOption("AI: Adaptive", new SlotOption.Faction(3))));
    }

    @Test
    void deserializeSlotOptionUnknownKeyAsOther() {
        assertThat(deserializePlayerOption("PlayerName", "Player1"), is(new GPGMessage.PlayerOption(123456,
                                                                                                    new SlotOption.Other(
                                                                                                            "PlayerName",
                                                                                                            new OptionValue.Str(
                                                                                                                    "Player1")))));
        assertThat(deserializeAIOption("Difficulty", 3), is(new GPGMessage.AIOption("AI: Adaptive",
                                                                                    new SlotOption.Other("Difficulty",
                                                                                                         new OptionValue.Int(
                                                                                                                 3)))));
    }

    @Test
    void deserializeSlotOptionNonNumericValueForNumericKeyAsInvalid() {
        assertThat(deserializePlayerOption("Team", "red"),
                   is(new GPGMessage.Invalid("PlayerOption", List.of("123456", "Team", "red"))));
        assertThat(deserializeAIOption("Faction", "uef"),
                   is(new GPGMessage.Invalid("AIOption", List.of("AI: Adaptive", "Faction", "uef"))));
    }

    @Test
    void deserializeSlotOptionWithUnsupportedValueTypeAsInvalid() {
        assertThat(deserializePlayerOption("Team", 1.5),
                   is(new GPGMessage.Invalid("PlayerOption", List.of("123456", "Team", 1.5))));
        assertThat(deserializeAIOption("Ready", true),
                   is(new GPGMessage.Invalid("AIOption", List.of("AI: Adaptive", "Ready", true))));
    }

    @Test
    void deserializeGPGClearSlot() {

        assertThat(deserializeGPGClient("""
                                        {"command": "ClearSlot", "target": "game", "args": [3]}
                                        """), is(new GPGMessage.ClearSlot(3)));
    }

    @Test
    void deserializeGPGGameModsActivated() {

        assertThat(deserializeGPGClient("""
                                        {"command": "GameMods", "target": "game", "args": ["activated", 1]}
                                        """), is(new GPGMessage.GameMods.Activated(1)));
    }

    @Test
    void deserializeGPGGameModsUids() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameMods", "target": "game", "args": ["uids", "e7846e9b-23a4-4b95-ae3a-fb69b289a585"]}
                                        """),
                   is(new GPGMessage.GameMods.Uids(Set.of("e7846e9b-23a4-4b95-ae3a-fb69b289a585"))));
    }

    @Test
    void deserializeGPGGameModsMultipleUidsAreSpaceSeparated() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameMods", "target": "game", "args": ["uids", "uid-a uid-b uid-c"]}
                                        """), is(new GPGMessage.GameMods.Uids(Set.of("uid-a", "uid-b", "uid-c"))));
    }

    @Test
    void deserializeGPGGameFull() {

        assertThat(deserializeGPGClient("""
                                        {"command": "GameFull", "target": "game", "args": []}
                                        """), is(new GPGMessage.GameFull()));
    }

    @Test
    void deserializeGPGChat() {

        assertThat(deserializeGPGClient("""
                                        {"command": "Chat", "target": "game", "args": ["gl hf"]}
                                        """), is(new GPGMessage.Chat("gl hf")));
    }

    @Test
    void deserializeGPGDisconnected() {

        assertThat(deserializeGPGClient("""
                                        {"command": "Disconnected", "target": "game", "args": ["123456"]}
                                        """), is(new GPGMessage.Disconnected("123456")));
    }

    @Test
    void deserializeGPGLaunchStatus() {

        assertThat(deserializeGPGClient("""
                                        {"command": "LaunchStatus", "target": "game", "args": ["Ready"]}
                                        """),
                   is(new GPGMessage.LaunchStatus(GPGMessage.LaunchStatus.Status.Known.READY)));
    }

    @Test
    void deserializeGPGLaunchStatusAllDocumentedValues() {
        Map<String, GPGMessage.LaunchStatus.Status> expected = Map.of("Connecting",
                                                                      GPGMessage.LaunchStatus.Status.Known.CONNECTING,
                                                                      "Missing local peers",
                                                                      GPGMessage.LaunchStatus.Status.Known.MISSING_LOCAL_PEERS,
                                                                      "Rejoining",
                                                                      GPGMessage.LaunchStatus.Status.Known.REJOINING,
                                                                      "Ready",
                                                                      GPGMessage.LaunchStatus.Status.Known.READY,
                                                                      "Ejected",
                                                                      GPGMessage.LaunchStatus.Status.Known.EJECTED,
                                                                      "Rejected",
                                                                      GPGMessage.LaunchStatus.Status.Known.REJECTED);

        expected.forEach((wireValue, status) -> assertThat(deserializeGPGClient("""
                                                                                {"command": "LaunchStatus", "target": "game", "args": ["%s"]}
                                                                                """.formatted(wireValue)),
                                                           is(new GPGMessage.LaunchStatus(status))));
    }

    @Test
    void deserializeGPGLaunchStatusUnknownValue() {
        // "Failed" is in the documented enum but has no Known constant
        assertThat(deserializeGPGClient("""
                                        {"command": "LaunchStatus", "target": "game", "args": ["Failed"]}
                                        """),
                   is(new GPGMessage.LaunchStatus(new GPGMessage.LaunchStatus.Status.Unknown("Failed"))));
    }

    @Test
    void deserializeGPGEstablishedPeer() {

        assertThat(deserializeGPGClient("""
                                        {"command": "EstablishedPeer", "target": "game", "args": ["123456"]}
                                        """), is(new GPGMessage.EstablishedPeer("123456")));
    }

    @Test
    void deserializeGPGDisconnectedPeer() {

        assertThat(deserializeGPGClient("""
                                        {"command": "DisconnectedPeer", "target": "game", "args": ["123456"]}
                                        """), is(new GPGMessage.DisconnectedPeer("123456")));
    }

    @Test
    void deserializeGPGGameResult() {

        assertThat(deserializeGPGClient("""
                                        {"command": "GameResult", "target": "game", "args": [1, "victory 10"]}
                                        """), is(new GPGMessage.GameResult(1, new ResultDetails(ResultDetails.Type.Known.VICTORY, 10, List.of()))));
    }

    /**
     * The doc's example signaling message: the JSON text of the adapter's CandidatesMessage.
     */
    private static final String CANDIDATES_MESSAGE = """
                                                     {
                                                     	"srcId": 101,
                                                     	"destId": 102,
                                                     	"password": "6cmvb8d7e03sma550p6dtta5vn",
                                                     	"ufrag": "eb31i1k68fj1m5",
                                                     	"candidates": [
                                                     		{
                                                     			"foundation": "1",
                                                     			"protocol": "udp",
                                                     			"priority": 2130706431,
                                                     			"ip": "192.0.2.77",
                                                     			"port": 6112,
                                                     			"type": "HOST_CANDIDATE",
                                                     			"generation": 0,
                                                     			"id": "abc",
                                                     			"relAddr": null,
                                                     			"relPort": 0
                                                     		}
                                                     	]
                                                     }""";

    private GPGMessage.Client deserializeIceMsg(Object... args) {
        return deserializeGPGClient(
                jsonMapper.writeValueAsString(Map.of("command", "IceMsg", "target", "game", "args", List.of(args))));
    }

    @Test
    void deserializeGPGIceMsg() {
        // The client forwards the adapter's onIceMsg as IceMsg [receiver id, msg text]
        assertThat(deserializeIceMsg(102, CANDIDATES_MESSAGE), is(new GPGMessage.IceMsg(102, CANDIDATES_MESSAGE)));
    }

    @Test
    void deserializeGPGIceMsgKeepsMessageTextVerbatim() {
        // The server only relays msg to the receiver, so it must not be reformatted; it is opaque even if not JSON
        String spaced = "{ \"srcId\" : 101 ,\n \"candidates\" : [ ] }";
        assertThat(deserializeIceMsg(102, spaced), is(new GPGMessage.IceMsg(102, spaced)));
        assertThat(deserializeIceMsg(102, "not json"), is(new GPGMessage.IceMsg(102, "not json")));
    }

    @Test
    void deserializeGPGIceMsgNonNumericReceiverAsInvalid() {
        assertThat(deserializeIceMsg("player", CANDIDATES_MESSAGE),
                   is(new GPGMessage.Invalid("IceMsg", List.of("player", CANDIDATES_MESSAGE))));
    }

    @Test
    void deserializeGPGIceMsgWrongArgCountAsInvalid() {
        assertThat(deserializeIceMsg(102), is(new GPGMessage.Invalid("IceMsg", List.of(102))));
        assertThat(deserializeIceMsg(101, 102, CANDIDATES_MESSAGE),
                   is(new GPGMessage.Invalid("IceMsg", List.of(101, 102, CANDIDATES_MESSAGE))));
    }

    @Test
    void deserializeGPGIceMsgMessageAsObjectAsInvalid() {
        // msg is JSON text, not a JSON object
        Map<String, Object> message = Map.of("srcId", 101, "destId", 102);
        assertThat(deserializeIceMsg(102, message), is(new GPGMessage.Invalid("IceMsg", List.of(102, message))));
    }

    /**
     * Wraps a JsonStats payload document in the GPG envelope, where it travels as a single string arg.
     */
    private GPGMessage.Client deserializeGPGJsonStats(String payloadJson) {
        return deserializeGPGClient(jsonMapper.writeValueAsString(
                Map.of("command", "JsonStats", "target", "game", "args", List.of(payloadJson))));
    }

    @Test
    void deserializeGPGJsonStats() {
        // The example from the doc's JsonStats schema
        GPGMessage.Client message = deserializeGPGJsonStats("""
                                                                   {
                                                                   	"stats": [
                                                                   		{
                                                                   			"name": "Player1",
                                                                   			"type": "Human",
                                                                   			"faction": 1,
                                                                   			"general": {
                                                                   				"score": 5370,
                                                                   				"lastupdatetick": 18240,
                                                                   				"currentunits": 87,
                                                                   				"currentcap": 1000,
                                                                   				"kills": {
                                                                   					"count": 11,
                                                                   					"mass": 18483.28,
                                                                   					"energy": 5002463
                                                                   				},
                                                                   				"built": {
                                                                   					"count": 19,
                                                                   					"mass": 532,
                                                                   					"energy": 4160
                                                                   				},
                                                                   				"lost": {
                                                                   					"count": 1,
                                                                   					"mass": 18000,
                                                                   					"energy": 5000000
                                                                   				}
                                                                   			},
                                                                   			"units": {
                                                                   				"land": {
                                                                   					"kills": 3,
                                                                   					"built": 12,
                                                                   					"lost": 1
                                                                   				},
                                                                   				"cdr": {
                                                                   					"kills": 1,
                                                                   					"built": 0,
                                                                   					"lost": 0
                                                                   				},
                                                                   				"sacu": {
                                                                   					"kills": 0,
                                                                   					"built": 0,
                                                                   					"lost": 0
                                                                   				}
                                                                   			},
                                                                   			"blueprints": {
                                                                   				"uel0001": {
                                                                   					"kills": 1,
                                                                   					"built": 0,
                                                                   					"lost": 0,
                                                                   					"lowest_health": 4210.5
                                                                   				}
                                                                   			},
                                                                   			"resources": {
                                                                   				"massin": {
                                                                   					"total": 1004.2,
                                                                   					"rate": 0.8,
                                                                   					"reclaimed": 120,
                                                                   					"reclaimRate": 0
                                                                   				},
                                                                   				"massout": {
                                                                   					"total": 950,
                                                                   					"rate": 0.7,
                                                                   					"excess": 12
                                                                   				},
                                                                   				"energyin": {
                                                                   					"total": 10129.7,
                                                                   					"rate": 4,
                                                                   					"reclaimed": 0,
                                                                   					"reclaimRate": 0
                                                                   				},
                                                                   				"energyout": {
                                                                   					"total": 9800,
                                                                   					"rate": 3.9,
                                                                   					"excess": 300
                                                                   				},
                                                                   				"storage": {
                                                                   					"storedMass": 54,
                                                                   					"storedEnergy": 330,
                                                                   					"maxMass": 650,
                                                                   					"maxEnergy": 4000
                                                                   				}
                                                                   			}
                                                                   		}
                                                                   	]
                                                                   }
                                                                   """);

        JsonStatsPayload.ArmyStats expectedArmy = new JsonStatsPayload.ArmyStats("Player1",
                                                                                 JsonStatsPayload.BrainType.Known.HUMAN,
                                                                                 Faction.UEF, null,
                                                                                 new JsonStatsPayload.General(5370,
                                                                                                              18240, 87,
                                                                                                              1000,
                                                                                                              new JsonStatsPayload.CountMassEnergy(
                                                                                                                      11,
                                                                                                                      18483.28,
                                                                                                                      5002463.0),
                                                                                                              new JsonStatsPayload.CountMassEnergy(
                                                                                                                      19,
                                                                                                                      532.0,
                                                                                                                      4160.0),
                                                                                                              new JsonStatsPayload.CountMassEnergy(
                                                                                                                      1,
                                                                                                                      18000.0,
                                                                                                                      5000000.0)),
                                                                                 Map.of("land",
                                                                                        new JsonStatsPayload.KillsBuiltLost(
                                                                                                3, 12, 1), "cdr",
                                                                                        new JsonStatsPayload.KillsBuiltLost(
                                                                                                1, 0, 0), "sacu",
                                                                                        new JsonStatsPayload.KillsBuiltLost(
                                                                                                0, 0, 0)),
                                                                                 Map.of("uel0001",
                                                                                        new JsonStatsPayload.BlueprintStats(
                                                                                                1, 0, 0, 4210.5)),
                                                                                 new JsonStatsPayload.Resources(
                                                                                         new JsonStatsPayload.ResourceIn(
                                                                                                 1004.2, 0.8, 120.0,
                                                                                                 0.0),
                                                                                         new JsonStatsPayload.ResourceIn(
                                                                                                 10129.7, 4.0, 0.0,
                                                                                                 0.0),
                                                                                         new JsonStatsPayload.ResourceOut(
                                                                                                 950.0, 0.7, 12.0),
                                                                                         new JsonStatsPayload.ResourceOut(
                                                                                                 9800.0, 3.9, 300.0),
                                                                                         new JsonStatsPayload.Storage(
                                                                                                 54.0, 330.0, 650.0,
                                                                                                 4000.0)));

        assertThat(message, is(new GPGMessage.JsonStats(new JsonStatsPayload(List.of(expectedArmy)))));
    }

    @Test
    void deserializeGPGJsonStatsWithCivilianGapDefeatedAndAi() {
        // A civilian army between two players leaves a null; Defeated appears once an army is defeated;
        // blueprints is optional
        GPGMessage.Client message = deserializeGPGJsonStats("""
                                                                   {
                                                                   	"stats": [
                                                                   		{
                                                                   			"name": "Player1",
                                                                   			"type": "Human",
                                                                   			"faction": 4,
                                                                   			"Defeated": -1,
                                                                   			"general": {
                                                                   				"score": 10
                                                                   			},
                                                                   			"units": {},
                                                                   			"resources": {}
                                                                   		},
                                                                   		null,
                                                                   		{
                                                                   			"name": "AI: Adaptive",
                                                                   			"type": "AI",
                                                                   			"faction": 3,
                                                                   			"general": {
                                                                   				"score": 20
                                                                   			},
                                                                   			"units": {},
                                                                   			"resources": {}
                                                                   		}
                                                                   	]
                                                                   }
                                                                   """);

        assertThat(message, instanceOf(GPGMessage.JsonStats.class));
        List<JsonStatsPayload.ArmyStats> stats = ((GPGMessage.JsonStats) message).payload().stats();
        assertThat(stats.size(), is(3));
        assertThat(stats.getFirst().faction(), is(Faction.SERAPHIM));
        assertThat(stats.getFirst().defeated(), is(-1.0));
        assertThat(stats.getFirst().blueprints(), nullValue());
        assertThat(stats.get(1), nullValue());
        assertThat(stats.getLast().type(), is(JsonStatsPayload.BrainType.Known.AI));
        assertThat(stats.getLast().faction(), is(Faction.CYBRAN));
        assertThat(stats.getLast().defeated(), nullValue());
    }

    @Test
    void deserializeGPGJsonStatsUnknownBrainType() {
        GPGMessage.Client message = deserializeGPGJsonStats("""
                                                                      {
                                                                      	"stats": [
                                                                      		{
                                                                      			"name": "Player1",
                                                                      			"type": "Spectator",
                                                                      			"faction": 1,
                                                                      			"general": {},
                                                                      			"units": {},
                                                                      			"resources": {}
                                                                      		}
                                                                      	]
                                                                      }""");

        assertThat(message, instanceOf(GPGMessage.JsonStats.class));
        JsonStatsPayload.ArmyStats armyStats = ((GPGMessage.JsonStats) message).payload().stats().getFirst();
        assertThat(armyStats, notNullValue());
        assertThat(armyStats.type(), is(new JsonStatsPayload.BrainType.Unknown("Spectator")));
    }

    @Test
    void deserializeGPGJsonStatsIgnoresUnknownFields() {
        // Fields the doc doesn't list (e.g. from a newer game build) must not invalidate the stats
        GPGMessage.Client message = deserializeGPGJsonStats("""
                                                                   {
                                                                   	"version": 2,
                                                                   	"stats": [
                                                                   		{
                                                                   			"name": "Player1",
                                                                   			"type": "Human",
                                                                   			"faction": 1,
                                                                   			"team": 2,
                                                                   			"general": {
                                                                   				"score": 10,
                                                                   				"apm": 120,
                                                                   				"kills": {
                                                                   					"count": 1,
                                                                   					"mass": 2,
                                                                   					"energy": 3,
                                                                   					"bonus": 4
                                                                   				}
                                                                   			},
                                                                   			"units": {
                                                                   				"land": {
                                                                   					"kills": 1,
                                                                   					"built": 2,
                                                                   					"lost": 3,
                                                                   					"damage": 4
                                                                   				}
                                                                   			},
                                                                   			"blueprints": {
                                                                   				"uel0001": {
                                                                   					"kills": 0,
                                                                   					"built": 0,
                                                                   					"lost": 0,
                                                                   					"veterancy": 5
                                                                   				}
                                                                   			},
                                                                   			"resources": {
                                                                   				"massin": {
                                                                   					"total": 1,
                                                                   					"peak": 9
                                                                   				},
                                                                   				"storage": {
                                                                   					"maxMass": 650,
                                                                   					"minMass": 0
                                                                   				},
                                                                   				"reclaim": {}
                                                                   			}
                                                                   		}
                                                                   	]
                                                                   }
                                                                   """);

        assertThat(message, instanceOf(GPGMessage.JsonStats.class));
        JsonStatsPayload.ArmyStats army = ((GPGMessage.JsonStats) message).payload().stats().getFirst();
        assertThat(army, notNullValue());
        assertThat(army.general().score(), is(10));
        assertThat(army.general().kills(), is(new JsonStatsPayload.CountMassEnergy(1, 2.0, 3.0)));
        assertThat(army.units(), is(Map.of("land", new JsonStatsPayload.KillsBuiltLost(1, 2, 3))));
        assertThat(army.blueprints(), is(Map.of("uel0001", new JsonStatsPayload.BlueprintStats(0, 0, 0, null))));
        assertThat(army.resources().massIn(), is(new JsonStatsPayload.ResourceIn(1.0, null, null, null)));
        assertThat(army.resources().storage(), is(new JsonStatsPayload.Storage(null, null, 650.0, null)));
    }

    @Test
    void deserializeGPGJsonStatsMalformedJsonAsInvalid() {
        assertThat(deserializeGPGJsonStats("{\"stats\":["),
                   is(new GPGMessage.Invalid("JsonStats", List.of("{\"stats\":["))));
    }

    @Test
    void deserializeGPGJsonStatsWrongShapeAsInvalid() {
        assertThat(deserializeGPGJsonStats("{\"stats\":\"none\"}"),
                   is(new GPGMessage.Invalid("JsonStats", List.of("{\"stats\":\"none\"}"))));
    }

    @Test
    void deserializeGPGJsonStatsNotAStringAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "JsonStats", "target": "game", "args": [5]}
                                        """), is(new GPGMessage.Invalid("JsonStats", List.of(5))));
    }

    @Test
    void deserializeGPGEnforceRating() {
        assertThat(deserializeGPGClient("""
                                        {"command": "EnforceRating", "target": "game", "args": []}
                                        """), is(new GPGMessage.EnforceRating()));
    }

    @Test
    void deserializeGPGGameEnded() {

        assertThat(deserializeGPGClient("""
                                        {"command": "GameEnded", "target": "game", "args": []}
                                        """), is(new GPGMessage.GameEnded()));
    }

    @Test
    void deserializeGPGTeamkillHappened() {

        assertThat(deserializeGPGClient("""
                                        {"command": "TeamkillHappened", "target": "game", "args": [412, "123456", "Victim", "654321", "Killer"]}
                                        """),
                   is(new GPGMessage.TeamkillHappened(412, "123456", "Victim", "654321", "Killer")));
    }

    @Test
    void deserializeGPGTeamkillHappenedWithIntZeroIdForAi() {
        // An id is the int 0 (not text) when the army has no client
        assertThat(deserializeGPGClient("""
                                        {"command": "TeamkillHappened", "target": "game", "args": [412, 0, "-", "654321", "Killer"]}
                                        """), is(new GPGMessage.TeamkillHappened(412, "0", "-", "654321", "Killer")));
    }

    @Test
    void deserializeGPGTeamkillReport() {
        assertThat(deserializeGPGClient("""
                                        {"command": "TeamkillReport", "target": "game", "args": [412, "123456", "Victim", "654321", "Killer"]}
                                        """),
                   is(new GPGMessage.TeamkillReport(412, "123456", "Victim", "654321", "Killer")));
    }

    @Test
    void deserializeGPGOperationComplete() {

        assertThat(deserializeGPGClient("""
                                        {"command": "OperationComplete", "target": "game", "args": [1, 0, "00:42:17"]}
                                        """), is(new GPGMessage.OperationComplete(true, false, "00:42:17")));
    }

    @Test
    void deserializeGPGRehost() {

        assertThat(deserializeGPGClient("""
                                        {"command": "Rehost", "target": "game", "args": []}
                                        """), is(new GPGMessage.Rehost()));
    }

    @Test
    void deserializeGPGBeat() {

        assertThat(deserializeGPGClient("""
                                        {"command": "BEAT", "target": "game", "args": [6000, 0]}
                                        """), is(new GPGMessage.Beat(6000, 0)));
    }

    @Test
    void deserializeGPGBottleneckDocExample() {
        GPGMessage.Client message = deserializeGPGClient("""
                                                         {"command": "Bottleneck", "target": "game", "args": ["data", "19508", "517268,516974,344419", "5980.1"]}
                                                         """);

        assertThat(message, is(new GPGMessage.Bottleneck(GPGMessage.Bottleneck.Kind.Known.DATA, 19508,
                                                         List.of(517268, 516974, 344419), 5980.1)));
    }

    @Test
    void deserializeGPGBottleneckAllKnownKinds() {
        Map<String, GPGMessage.Bottleneck.Kind> expected = Map.of("nothing", GPGMessage.Bottleneck.Kind.Known.NOTHING,
                                                                  "readiness",
                                                                  GPGMessage.Bottleneck.Kind.Known.READINESS, "data",
                                                                  GPGMessage.Bottleneck.Kind.Known.DATA, "ack",
                                                                  GPGMessage.Bottleneck.Kind.Known.ACK);

        expected.forEach((wireValue, kind) -> assertThat(deserializeGPGClient("""
                                                                              {"command": "Bottleneck", "target": "game", "args": ["%s", "1", "2", "3.0"]}
                                                                              """.formatted(wireValue)),
                                                         is(new GPGMessage.Bottleneck(kind, 1, List.of(2), 3.0))));
    }

    @Test
    void deserializeGPGBottleneckUnknownKindAndNoIds() {
        // "unknown" is a documented kind with no Known constant; the id list may be empty
        assertThat(deserializeGPGClient("""
                                        {"command": "Bottleneck", "target": "game", "args": ["unknown", "19508", "", "0.0"]}
                                        """),
                   is(new GPGMessage.Bottleneck(new GPGMessage.Bottleneck.Kind.Unknown("unknown"), 19508, List.of(),
                                                0.0)));
    }

    @Test
    void deserializeGPGBottleneckCleared() {

        assertThat(deserializeGPGClient("""
                                        {"command": "BottleneckCleared", "target": "game", "args": []}
                                        """), is(new GPGMessage.BottleneckCleared()));
    }

    @Test
    void deserializeGPGDesync() {

        assertThat(deserializeGPGClient("""
                                        {"command": "Desync", "target": "game", "args": [1234, 1, "9e107d9d372bb6826bd81d3542a419d6", "e4d909c290d0fb1ca068ffaddf22cbd0"]}
                                        """), is(new GPGMessage.Desync(1234, 1, "9e107d9d372bb6826bd81d3542a419d6",
                                                                       "e4d909c290d0fb1ca068ffaddf22cbd0")));
    }

    @Test
    void deserializeGPGStats() {

        assertThat(deserializeGPGClient("""
                                        {"command": "Stats", "target": "game", "args": ["<?xml version=\\"1.0\\"?><GameStats></GameStats>"]}
                                        """),
                   is(new GPGMessage.XMLStats("<?xml version=\"1.0\"?><GameStats></GameStats>")));
    }

    @Test
    void deserializeGPGProcessNatPacket() {
        // Data arguments (wire tag 2) are base64 in JSON
        GPGMessage.Client message = deserializeGPGClient("""
                                                         {"command": "ProcessNatPacket", "target": "game", "args": ["192.168.0.1:6112", "AQID"]}
                                                         """);

        assertThat(message, instanceOf(GPGMessage.ProcessNatPacket.class));
        GPGMessage.ProcessNatPacket packet = (GPGMessage.ProcessNatPacket) message;
        assertThat(packet.fromIpPort(), is("192.168.0.1:6112"));
        assertThat(packet.data(), equalTo(new byte[]{1, 2, 3}));
    }

    @Test
    void deserializeGPGUnrecognizedCommandAsUnknown() {

        assertThat(deserializeGPGClient("""
                                        {"command": "RejoinOffer", "target": "game", "args": ["123456", 7]}
                                        """), is(new GPGMessage.Unknown("RejoinOffer", List.of("123456", 7))));
    }

    @Test
    void deserializeGPGMessageWithoutTarget() {
        // target is optional in the Envelope schema
        assertThat(deserializeGPGClient("""
                                        {"command": "Chat", "args": ["gl hf"]}
                                        """), is(new GPGMessage.Chat("gl hf")));
    }

    // ---- GPG Unknown: commands with no registered subtype ----

    @Test
    void deserializeGPGUnrecognizedCommandWithoutArgsAsUnknown() {

        assertThat(deserializeGPGClient("""
                                        {"command": "SomethingNew", "target": "game", "args": []}
                                        """), is(new GPGMessage.Unknown("SomethingNew", List.of())));
    }

    @Test
    void deserializeGPGToGameCommandSentByClientAsUnknown() {
        // Messages to the game (doc section 5) are not valid client messages
        assertThat(deserializeGPGClient("""
                                        {"command": "CreateLobby", "target": "game", "args": [0, 6112, "Player1", 123456, 1]}
                                        """),
                   is(new GPGMessage.Unknown("CreateLobby", List.of(0, 6112, "Player1", 123456, 1))));
    }

    // ---- GPG Invalid: known commands whose args do not fit ----

    @Test
    void deserializeGPGNonNumericStringForIntArgAsInvalid() {

        assertThat(deserializeGPGClient("""
                                        {"command": "Bottleneck", "target": "game", "args": ["nothing", "a", "5,6,7", "6000.1"]}
                                        """),
                   is(new GPGMessage.Invalid("Bottleneck", List.of("nothing", "a", "5,6,7", "6000.1"))));
    }

    @Test
    void deserializeGPGNonNumericIdListAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "Bottleneck", "target": "game", "args": ["data", "1", "5,x", "1.0"]}
                                        """),
                   is(new GPGMessage.Invalid("Bottleneck", List.of("data", "1", "5,x", "1.0"))));
    }

    @Test
    void deserializeGPGStringForIntArgAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "ClearSlot", "target": "game", "args": ["three"]}
                                        """), is(new GPGMessage.Invalid("ClearSlot", List.of("three"))));
    }

    @Test
    void deserializeGPGNonNumericPlayerOptionOwnerIdAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "PlayerOption", "target": "game", "args": ["not-a-uid", "Faction", 2]}
                                        """),
                   is(new GPGMessage.Invalid("PlayerOption", List.of("not-a-uid", "Faction", 2))));
    }

    @Test
    void deserializeGPGDesyncWithStringBeatAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "Desync", "target": "game", "args": ["beat", 1, "aa", "bb"]}
                                        """), is(new GPGMessage.Invalid("Desync", List.of("beat", 1, "aa", "bb"))));
    }

    @Test
    void deserializeGPGGameModsUnknownTypeAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameMods", "target": "game", "args": ["something", 1]}
                                        """), is(new GPGMessage.Invalid("GameMods", List.of("something", 1))));
    }

    @Test
    void deserializeGPGGameModsMismatchedArgTypeAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameMods", "target": "game", "args": ["uids", 3]}
                                        """), is(new GPGMessage.Invalid("GameMods", List.of("uids", 3))));
        assertThat(deserializeGPGClient("""
                                        {"command": "GameMods", "target": "game", "args": ["activated", "3"]}
                                        """), is(new GPGMessage.Invalid("GameMods", List.of("activated", "3"))));
    }

    @Test
    void deserializeGPGTooFewArgsAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameResult", "target": "game", "args": [1]}
                                        """), is(new GPGMessage.Invalid("GameResult", List.of(1))));
    }

    @Test
    void deserializeGPGTooManyArgsAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "Chat", "target": "game", "args": ["gl hf", "extra"]}
                                        """), is(new GPGMessage.Invalid("Chat", List.of("gl hf", "extra"))));
    }

    @Test
    void deserializeGPGGameModsTooManyArgsAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameMods", "target": "game", "args": ["activated", 1, 2]}
                                        """), is(new GPGMessage.Invalid("GameMods", List.of("activated", 1, 2))));
    }

    @Test
    void deserializeGPGGameModsTooFewArgsAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameMods", "target": "game", "args": ["activated"]}
                                        """), is(new GPGMessage.Invalid("GameMods", List.of("activated"))));
    }

    @Test
    void deserializeGPGArgsForNoArgCommandAsInvalid() {
        assertThat(deserializeGPGClient("""
                                        {"command": "GameEnded", "target": "game", "args": [1]}
                                        """), is(new GPGMessage.Invalid("GameEnded", List.of(1))));
    }

    // ---- GPG Server messages ----

    @Test
    void serializeHostGame() throws JSONException {
        String json = serializeGPGServer(new GPGMessage.HostGame("test"));

        JSONAssert.assertEquals("""
                                {"command":"HostGame", "target":"game", "args": ["test"]}
                                """, json, JSONCompareMode.STRICT);
    }

    @Test
    void serializeHostGameWithMapFolderName() throws JSONException {
        // The lobby server sends the map's folder name; the game builds /maps/<name>/<name>_scenario.lua from it
        JSONAssert.assertEquals("""
                                {"command": "HostGame", "target": "game", "args": ["scmp_009"]}
                                """, serializeGPGServer(new GPGMessage.HostGame("scmp_009")), JSONCompareMode.STRICT);
    }

    @Test
    void serializeJoinGame() throws JSONException {
        // [hostName, hostUid]; the ICE adapter puts its local endpoint for the host in front before the game sees it
        JSONAssert.assertEquals("""
                                {"command": "JoinGame", "target": "game", "args": ["HostPlayer", 654321]}
                                """, serializeGPGServer(new GPGMessage.JoinGame("HostPlayer", 654321)),
                                JSONCompareMode.STRICT);
    }

    @Test
    void serializeConnectToPeer() throws JSONException {
        // [name, uid, offer]; the ICE adapter keeps offer and sends the game [endpoint, name, uid]
        JSONAssert.assertEquals("""
                                {"command": "ConnectToPeer", "target": "game", "args": ["OtherPlayer", 111111, true]}
                                """, serializeGPGServer(new GPGMessage.ConnectToPeer("OtherPlayer", 111111, true)),
                                JSONCompareMode.STRICT);
        JSONAssert.assertEquals("""
                                {"command": "ConnectToPeer", "target": "game", "args": ["OtherPlayer", 111111, false]}
                                """, serializeGPGServer(new GPGMessage.ConnectToPeer("OtherPlayer", 111111, false)),
                                JSONCompareMode.STRICT);
    }

    @Test
    void serializeDisconnectFromPeer() throws JSONException {
        JSONAssert.assertEquals("""
                                {"command": "DisconnectFromPeer", "target": "game", "args": [111111]}
                                """, serializeGPGServer(new GPGMessage.DisconnectFromPeer(111111)),
                                JSONCompareMode.STRICT);
    }

    @Test
    void serializeIceMsg() throws JSONException {
        // Relayed to the receiver as IceMsg [sender id, msg]; msg stays the CandidatesMessage text, not an object
        String json = serializeGPGServer(new GPGMessage.IceMsg(101, CANDIDATES_MESSAGE));

        JSONAssert.assertEquals("""
                                {"command": "IceMsg", "target": "game", "args": [101, %s]}
                                """.formatted(jsonMapper.writeValueAsString(CANDIDATES_MESSAGE)), json,
                                JSONCompareMode.STRICT);
    }

    @Test
    void serverMessagesRoundTripThroughClientDeserializerWhereShared() {
        // IceMsg is both a client and a server message with the same wire shape
        GPGMessage.IceMsg iceMsg = new GPGMessage.IceMsg(101, CANDIDATES_MESSAGE);
        assertThat(deserializeGPGClient(serializeGPGServer(iceMsg)), is(iceMsg));
    }

    // ---- Faction wire formats ----

    @Test
    void factionDeserializesFromNameAndIndex() {
        assertThat(jsonMapper.readValue("\"seraphim\"", Faction.class), is(Faction.SERAPHIM));
        assertThat(jsonMapper.readValue("4", Faction.class), is(Faction.SERAPHIM));
    }
}
