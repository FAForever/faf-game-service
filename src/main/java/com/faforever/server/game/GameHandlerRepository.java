package com.faforever.server.game;

import com.faforever.server.player.dto.LeaderboardRating;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
interface GameHandlerRepository {

    @Query("""
           select new LeaderboardRating(lr.leaderboard.technicalName, lr.mean, lr.deviation)
           from LeaderboardRatingEntity lr
           where lr.playerId = :playerId and lr.leaderboard.technicalName = :leaderboard
           """)
    Optional<LeaderboardRating> loadPlayerRating(int playerId, String leaderboard);

}
