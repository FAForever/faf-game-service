package com.faforever.server.player;

public record LeaderboardRating(
        String leaderboard,
        int totalGames,
        double mean,
        double deviation
) {

    public double displayedRating() {
        return mean - deviation * 3;
    }

}
