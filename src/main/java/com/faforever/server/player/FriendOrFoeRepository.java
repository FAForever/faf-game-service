package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.domain._FriendOrFoeEntity;
import jakarta.data.repository.By;
import jakarta.data.repository.Delete;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Save;
import jakarta.data.repository.Select;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface FriendOrFoeRepository {

    @Find
    @Select(_FriendOrFoeEntity.ID_SUBJECT_ID)
    @Transactional
    List<Integer> loadFriendsAndFoes(@By(_FriendOrFoeEntity.ID_PLAYER_ID) int playerId, @By(_FriendOrFoeEntity.STATUS)
                                               FriendOrFoeEntity.Status status);

}
