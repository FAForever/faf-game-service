package com.faforever.server.game;

import com.faforever.server.message.client.dto.GameType;
import org.jspecify.annotations.Nullable;

public record GameDetails(
        Visibility visibility,
        @Nullable String password,
        String leaderboard,
        GameType gameType,
        String featuredMod,
        GamePlayer host,
        RatingRange ratingRange
) {}
