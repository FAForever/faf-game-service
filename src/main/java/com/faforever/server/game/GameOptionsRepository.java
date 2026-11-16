package com.faforever.server.game;

import com.faforever.server.game.dto.FAFMap;
import com.faforever.server.game.dto.SimMod;
import io.quarkus.cache.CacheResult;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
interface GameOptionsRepository {

    @Query("""
        select new Vault(mv.uid, mv.mod.name, mv.ranked) from ModVersionEntity mv where mv.uid = :uid
    """)
    Optional<SimMod.Vault> findSimMod(String uid);

    @CacheResult(cacheName = "mod-version")
    default SimMod findSimModOrUnknown(String uid) {
        SimMod.Vault vaultMod = findSimMod(uid).orElse(null);
        if (vaultMod != null) {
            return vaultMod;
        }
        return new SimMod.Unknown(uid);
    }

    @Query("""
        select new Vault(mv.id, mv.map.name, mv.ranked) from MapVersionEntity mv where mv.mapFolderName = :mapName
    """)
    @Transactional(Transactional.TxType.MANDATORY)
    Optional<FAFMap.Vault> findMap(String mapName);

    @CacheResult(cacheName = "map-version")
    default FAFMap findMapOrUnknown(String mapName) {
        FAFMap.Vault vaultMap = findMap(mapName).orElse(null);
        if (vaultMap != null) {
            return vaultMap;
        }
        return new FAFMap.Unknown(mapName);
    }
}
