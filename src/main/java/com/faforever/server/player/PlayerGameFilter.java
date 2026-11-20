package com.faforever.server.player;

import com.faforever.server.player.dto.LeaderboardRating;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@FunctionalInterface
public interface PlayerGameFilter {

    PlayerGameFilter VISIBLE_TO_ALL = (_, _) -> false;
    PlayerGameFilter VISIBLE_TO_NONE = (_, _) -> true;

    boolean isVisible(int playerId, Function<String, Optional<LeaderboardRating>> ratingFunction);

    static boolean isConstant(PlayerGameFilter playerGameFilter) {
        return playerGameFilter == VISIBLE_TO_ALL || playerGameFilter == VISIBLE_TO_NONE;
    }
}
