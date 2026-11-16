package com.faforever.server.game.dto;

public sealed interface SimMod {

    String uid();

    String name();

    boolean ranked();

    record Vault(String uid, String name, boolean ranked) implements SimMod {}

    record Unknown(String uid) implements SimMod {
        @Override
        public String name() {
            return "Unknown Sim Mod";
        }

        @Override
        public boolean ranked() {
            return false;
        }
    }

}
