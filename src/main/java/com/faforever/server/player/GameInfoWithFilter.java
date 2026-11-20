package com.faforever.server.player;

import com.faforever.server.message.client.dto.GameInfo;

import java.util.function.Predicate;

public record GameInfoWithFilter(
        GameInfo gameInfo,
        PlayerGameFilter filter
) {}
