package com.faforever.server.game;

import com.faforever.server.message.client.dto.OptionValue;
import com.faforever.server.message.client.dto.SlotOption;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
class SlotProperties {
    private int teamIndex = -1;
    private int colorIndex = -1;
    private int slot = -1;
    private int army = -1;
    private int factionIndex = 5;
    private final Map<String, OptionValue> otherOptions = new ConcurrentHashMap<>();

    void handleSlotOption(SlotOption slotProperty) {
        switch (slotProperty) {
            case SlotOption.Slot(int value) -> slot = value;
            case SlotOption.Faction(int value) -> factionIndex = value;
            case SlotOption.Team(int value) -> teamIndex = value;
            case SlotOption.Color(int value) -> colorIndex = value;
            case SlotOption.Army(int value) -> army = value;
            case SlotOption.Other(String key, OptionValue value) -> otherOptions.put(key, value);
        }
    }
}
