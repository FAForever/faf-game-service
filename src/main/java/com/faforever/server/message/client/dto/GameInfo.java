package com.faforever.server.message.client.dto;

import com.faforever.server.message.client.GameMessage;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record GameInfo(
        int uid,
        String title,
        String host,
        GameType gameType,
        int maxPlayers,
        int numPlayers,
        GameVisibility visibility,
        boolean passwordProtected,
        GameStatus state,
        String featuredMod,
        @JsonProperty("rating_type") String leaderboard,
        Map<String, String> simMods,
        String mapName,
        String mapFilePath,
        @Nullable Instant hostedAt,
        @Nullable Long launchedAt,
        @Nullable String ratingType,
        @Nullable Integer ratingMin,
        @Nullable Integer ratingMax,
        boolean enforceRatingRange,
        Map<Integer, Set<String>> teams,
        Set<Team> teamIds
) {

    // GameInfoList is technically recursive and the client expects the inner elements to have the command
    @JsonProperty("command")
    public String command() {
        return GameMessage.GameInfoList.COMMAND;
    }

    public record Team(
            int teamId,
            Set<Integer> playerIds
    ) {}

}
