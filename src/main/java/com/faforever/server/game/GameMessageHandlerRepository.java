package com.faforever.server.game;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.domain.PlayerEntity;
import com.faforever.server.domain._FriendOrFoeEntity;
import com.faforever.server.game.dto.GamePlayer;
import jakarta.annotation.Nullable;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Select;
import jakarta.transaction.Transactional;

import java.util.List;

@Repository
@Transactional
interface GameMessageHandlerRepository {

    @Find(FriendOrFoeEntity.class)
    @Select("id.subjectId")
    List<Integer> loadFriendsAndFoes(@By(_FriendOrFoeEntity.ID_PLAYER_ID) int playerId, @By(_FriendOrFoeEntity.STATUS)
    FriendOrFoeEntity.Status status);


    @Find(PlayerEntity.class)
    @Select("id")
    @Select("name")
    @Nullable GamePlayer loadPlayer(@By(By.ID) int id);
}
