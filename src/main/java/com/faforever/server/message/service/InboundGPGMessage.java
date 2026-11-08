package com.faforever.server.message.service;

import com.faforever.server.message.client.GPGMessage;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public record InboundGPGMessage<T extends GPGMessage.Client>(
        long sessionId,
        int playerId,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
        T message
) {}
