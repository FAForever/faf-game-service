package com.faforever.server.message.service;

import com.faforever.server.message.client.GPGMessage;

public record OutboundGPGMessage<T extends GPGMessage.Server>(
        OutboundTarget target,
        GPGMessage.Wrapped<T> message
) {

    public static <T extends GPGMessage.Server> OutboundGPGMessage<GPGMessage.Server> forSession(long sessionId, T message) {
        return new OutboundGPGMessage<>(new OutboundTarget.Session(sessionId), new GPGMessage.Wrapped<>(message));
    }
}
