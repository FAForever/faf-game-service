package com.faforever.server.player;

import com.faforever.server.domain.PlayerEntity;
import com.faforever.server.rating.LeaderboardRating;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Player {

    @Getter
    private final int id;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String username;

    private final Map<String, LeaderboardRating> leaderboardRatings = new ConcurrentHashMap<>();

    @Getter
    private Set<Integer> friends = Set.of();
    @Getter
    private Set<Integer> foes = Set.of();

    @Getter
    private State state = State.IDLE;

    private Player(int id, String username) {
        this.id = id;
        this.username = username;
    }

    static Player from(PlayerEntity playerEntity) {
        return new Player(playerEntity.getId(), playerEntity.getName());
    }

    void markOffline() {
        state = State.OFFLINE;
    }

    public void updateLeaderboardRatings(Collection<LeaderboardRating> leaderboardRatings) {
        for (LeaderboardRating leaderboardRating : leaderboardRatings) {
            this.leaderboardRatings.put(leaderboardRating.leaderboard(), leaderboardRating);
        }
    }

    public Set<LeaderboardRating> getLeaderboardRatings() {
        return Set.copyOf(leaderboardRatings.values());
    }

    public void setFriends(Set<Integer> friends) {
        this.friends = Set.copyOf(friends);
    }

    public void setFoes(Set<Integer> foes) {
        this.foes = Set.copyOf(foes);
    }

    public boolean isFriend(int playerId) {
        return friends.contains(playerId);
    }

    public boolean isFoe(int playerId) {
        return foes.contains(playerId);
    }

    public enum State {
        IDLE, PLAYING, HOSTING, JOINING, STARTING_GAME, RESERVED, OFFLINE
    }

    public Optional<LeaderboardRating> getRating(String leaderboard) {
        return Optional.ofNullable(leaderboardRatings.get(leaderboard));
    }
}
