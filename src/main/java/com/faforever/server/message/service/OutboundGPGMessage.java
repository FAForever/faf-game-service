package com.faforever.server.message.service;

import com.faforever.server.message.client.GPGMessage;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public record OutboundGPGMessage<T extends GPGMessage.Server>(
        OutboundTarget target,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
        T message
) {

    public static <T extends GPGMessage.Server> OutboundGPGMessage<GPGMessage.Server> forSession(long sessionId, T message) {
        return new OutboundGPGMessage<>(new OutboundTarget.Session(sessionId), message);
    }
}
