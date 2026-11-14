package com.faforever.server.player;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Player {

    @Getter
    private final int id;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String username;

    private final Map<String, LeaderboardRating> leaderboardRatings = new ConcurrentHashMap<>();


    private Player(int id, String username) {
        this.id = id;
        this.username = username;
    }

    public void updateLeaderboardRatings(Collection<LeaderboardRating> leaderboardRatings) {
        for (LeaderboardRating leaderboardRating : leaderboardRatings) {
            this.leaderboardRatings.put(leaderboardRating.leaderboard(), leaderboardRating);
        }
    }

    public Optional<LeaderboardRating> getRating(String leaderboard) {
        return Optional.ofNullable(leaderboardRatings.get(leaderboard));
    }
}
