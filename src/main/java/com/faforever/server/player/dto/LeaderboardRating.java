package com.faforever.server.player.dto;

public record LeaderboardRating(
        String leaderboard,
        double mean,
        double deviation
) {

    public double displayedRating() {
        return mean - deviation * 3;
    }

}
