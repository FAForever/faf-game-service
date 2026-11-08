package com.faforever.server.game;

import com.faforever.server.message.client.dto.Faction;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record LaunchDetails(
        long sessionId,
        GamePlayer player,
        Map<String, String> gameOptions,
        @Nullable Integer mapPosition,
        @Nullable Integer expectedPlayers,
        @Nullable Integer team,
        @Nullable Faction faction
) {}
