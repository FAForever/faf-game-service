package com.faforever.server.game;

import com.faforever.server.message.client.GameOption;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameOptions {

    private final Map<String, SlotOptions> aiOptions = new ConcurrentHashMap<>();
    private final Map<Integer, SlotOptions> playerOptions = new ConcurrentHashMap<>();
    private final Map<String, GameOption> options = new ConcurrentHashMap<>();

}
