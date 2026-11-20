package com.faforever.server.player;

import com.faforever.server.player.dto.LeaderboardRating;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class Player {

    @Getter
    private final int id;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String username;

    private final Map<String, LeaderboardRating> leaderboardRatings = new ConcurrentHashMap<>();
    private final Set<Long> sessionIds = ConcurrentHashMap.newKeySet();


    Player(int id, String username) {
        this.id = id;
        this.username = username;
    }

    void updateLeaderboardRatings(Collection<LeaderboardRating> leaderboardRatings) {
        for (LeaderboardRating leaderboardRating : leaderboardRatings) {
            this.leaderboardRatings.put(leaderboardRating.leaderboard(), leaderboardRating);
        }
    }

    void addSession(long sessionId) {
        sessionIds.add(sessionId);
    }

    void removeSession(long sessionId) {
        sessionIds.remove(sessionId);
    }

    boolean hasAnySession() {
        return !sessionIds.isEmpty();
    }

    boolean hasSession(long sessionId) {
        return sessionIds.contains(sessionId);
    }

    Optional<LeaderboardRating> getRating(String leaderboard) {
        return Optional.ofNullable(leaderboardRatings.get(leaderboard));
    }
}
