package com.faforever.server.game.dto;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

public sealed interface SlotHolder {

    sealed interface Occupied extends SlotHolder {}

    record AI(String name) implements Occupied {}

    record Player(int playerId) implements Occupied {}

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    final class Empty implements SlotHolder {
        public static final Empty INSTANCE = new Empty();
    }
}
