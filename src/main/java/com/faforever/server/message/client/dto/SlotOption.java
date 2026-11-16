package com.faforever.server.message.client.dto;

import java.util.Locale;

public sealed interface SlotOption {

    record Slot(int value) implements SlotOption {}

    record Team(int value) implements SlotOption {}

    record Color(int value) implements SlotOption {}

    record Faction(int value) implements SlotOption {}
    record Army(int value) implements SlotOption {}

    record Other(
            String key,
            OptionValue value
    ) implements SlotOption {}

    static SlotOption of(String key, OptionValue value) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "team" -> new Team(value.intValue());
            case "startspot" -> new Slot(value.intValue());
            case "color", "playercolor" -> new Color(value.intValue());
            case "faction" -> new Faction(value.intValue());
            case "army" -> new Army(value.intValue());
            default -> new Other(key, value);
        };
    }
}
