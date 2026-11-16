package com.faforever.server.player;

import com.faforever.server.player.dto.LeaderboardRating;
import com.faforever.server.player.dto.PlayerLeaderboardRating;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
interface PlayerTrackerRepository {

    @Query("""
           select new Player(p.id, p.name)
           from PlayerEntity p
           where p.id = :playerId
           """)
    Optional<Player> loadPlayer(int playerId);

    @Query("""
           select new PlayerLeaderboardRating(lr.playerId, new com.faforever.server.player.dto.LeaderboardRating(lr.leaderboard.technicalName, lr.mean, lr.deviation))
           from LeaderboardRatingEntity lr
           where lr.playerId in :playerIds and lr.updateTime >= :timeCutoff
           """)
    List<PlayerLeaderboardRating> loadUpdatedLeaderboardRatings(Collection<Integer> playerIds, Instant timeCutoff);

    @Query("""
           select new LeaderboardRating(lr.leaderboard.technicalName, lr.mean, lr.deviation)
           from LeaderboardRatingEntity lr
           where lr.playerId = :playerId
           """)
    List<LeaderboardRating> loadPlayerRatings(int playerId);
}
