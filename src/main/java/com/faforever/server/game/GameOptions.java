package com.faforever.server.game;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameOptions {

    private final Map<String, Map<String, Object>> aiOptions = new ConcurrentHashMap<>();
    private final Map<Integer, Map<String, Object>> playerOptions = new ConcurrentHashMap<>();
    private final Map<String, Object> options = new ConcurrentHashMap<>();

}
