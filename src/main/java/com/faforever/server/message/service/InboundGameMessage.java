package com.faforever.server.message.service;

import com.faforever.server.message.client.GameMessage;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public record InboundGameMessage<T extends GameMessage.Client>(
        long sessionId,
        int playerId,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
        T message
) {}
