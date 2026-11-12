package com.faforever.server.message.service;

import com.faforever.server.message.client.GPGMessage;

public record InboundGPGMessage<T extends GPGMessage.Client>(
        long sessionId,
        int playerId,
        GPGMessage.Wrapped<T> message
) {}
