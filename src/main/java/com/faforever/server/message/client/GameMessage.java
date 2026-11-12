package com.faforever.server.message.client;

import com.faforever.server.message.client.dto.Faction;
import com.faforever.server.message.client.dto.GameAccess;
import com.faforever.server.message.client.dto.GameInfo;
import com.faforever.server.message.client.dto.GameType;
import com.faforever.server.message.client.dto.GameVisibility;
import com.faforever.server.message.client.dto.LobbyMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public sealed interface GameMessage {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({@JsonSubTypes.Type(value = HostGameRequest.class, name = HostGameRequest.COMMAND),
                   @JsonSubTypes.Type(value = JoinGameRequest.class, name = JoinGameRequest.COMMAND),
                   @JsonSubTypes.Type(value = RestoreGameSessionRequest.class, name = RestoreGameSessionRequest.COMMAND),})
    sealed interface Client extends GameMessage {

        String requestId();

        String command();

    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
    @JsonSubTypes({@JsonSubTypes.Type(value = GameInfoList.class, name = GameInfoList.COMMAND),
                   @JsonSubTypes.Type(value = GameLaunchResponse.class, name = GameLaunchResponse.COMMAND),
                   @JsonSubTypes.Type(value = GameJoinFailed.class, name = GameJoinFailed.COMMAND),

    })
    sealed interface Server extends GameMessage {}

    record GameInfoList(
            Collection<GameInfo> games
    ) implements Server {

        public static final String COMMAND = "game_info";
    }

    record GameJoinFailed(
            String reason,
            @JsonProperty("uid") int gameId
    ) implements Server {

        public static final String COMMAND = "game_join_failed";
    }

    record GameLaunchResponse(
            @JsonProperty("uid") int gameId,
            String name,
            @JsonProperty("mod") String featuredMod,
            @JsonProperty("init_mode") LobbyMode lobbyMode,
            GameType gameType,
            @JsonProperty("rating_type") String leaderboard,
            @JsonProperty("mapname") String mapName,
            @Nullable Integer mapPosition,
            @Nullable Integer expectedPlayers,
            Map<String, String> gameOptions,
            @Nullable Integer team,
            @Nullable Faction faction
    ) implements Server {

        public static final String COMMAND = "game_launch";

        @JsonProperty("args")
        public List<String> args() {
            return List.of();
        }
    }

    record HostGameRequest(
            String requestId,
            @JsonProperty("mapname") @Nullable String mapName,
            String title,
            @JsonProperty("mod") @Nullable String featuredMod,
            @Deprecated boolean[] options,
            @Deprecated Integer version,
            GameAccess access,
            @Nullable String password,
            GameVisibility visibility,
            @Nullable Integer ratingMin,
            @Nullable Integer ratingMax,
            Boolean enforceRatingRange
    ) implements Client {

        public static final String COMMAND = "game_host";

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record JoinGameRequest(
            String requestId,
            @JsonProperty("uid") int gameId,
            @Nullable String password
    ) implements Client {

        public static final String COMMAND = "game_join";

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record RestoreGameSessionRequest(
            String requestId,
            int gameId
    ) implements Client {

        public static final String COMMAND = "restore_game_session";

        @Override
        public String command() {
            return COMMAND;
        }
    }
}
