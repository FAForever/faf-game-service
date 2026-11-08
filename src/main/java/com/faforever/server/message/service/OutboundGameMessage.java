package com.faforever.server.message.service;

import com.faforever.server.message.client.GameMessage;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public record OutboundGameMessage<T extends GameMessage.Server>(
        OutboundTarget target,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "command")
        T message
) {

    public static <T extends GameMessage.Server> OutboundGameMessage<GameMessage.Server> forPlayer(int playerId, T message) {
        return new OutboundGameMessage<>(new OutboundTarget.Player(playerId), message);
    }

    public static <T extends GameMessage.Server> OutboundGameMessage<GameMessage.Server> forSession(long sessionId, T message) {
        return new OutboundGameMessage<>(new OutboundTarget.Session(sessionId), message);
    }

}
