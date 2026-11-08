package com.faforever.server.game;

import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

@Repository
interface GameRepository {

    @Query("select max(g.id) from GameEntity g")
    int findMaxGameId();
}
