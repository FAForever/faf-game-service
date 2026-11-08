package com.faforever.server.message.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") @Nullable Instant hostedAt,
        @Nullable Long launchedAt,
        @Nullable String ratingType,
        @Nullable Integer ratingMin,
        @Nullable Integer ratingMax,
        boolean enforceRatingRange,
        Map<Integer, Set<String>> teams,
        Set<Team> teamIds
) {

    public record Team(
            int teamId,
            Set<Integer> playerIds
    ) {}

}
