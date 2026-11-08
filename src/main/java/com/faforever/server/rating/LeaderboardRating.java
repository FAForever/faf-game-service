package com.faforever.server.rating;

import com.faforever.server.domain.LeaderboardRatingEntity;

public record LeaderboardRating(
        String leaderboard,
        int totalGames,
        double mean,
        double deviation
) {

    public double displayedRating() {
        return mean - deviation * 3;
    }

    public static LeaderboardRating from(LeaderboardRatingEntity leaderboardRatingEntity) {
        return new LeaderboardRating(leaderboardRatingEntity.getLeaderboard().getTechnicalName(),
                                     leaderboardRatingEntity.getTotalGames(), leaderboardRatingEntity.getMean(),
                                     leaderboardRatingEntity.getDeviation());
    }

}
