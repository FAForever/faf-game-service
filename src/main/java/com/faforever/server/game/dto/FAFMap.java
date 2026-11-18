package com.faforever.server.game.dto;

public sealed interface FAFMap {

    String mapName();

    boolean ranked();

    record Vault(
            int mapVersionId,
            String mapName,
            boolean ranked
    ) implements FAFMap {}

    record Neroxis(String mapName) implements FAFMap {

        public static final String PREFIX = "neroxis_map_generator";

        public Neroxis {
            if (!mapName.startsWith(PREFIX)) {
                throw new IllegalArgumentException(
                        "Map name does not start with neroxis_map_generator: %s".formatted(mapName));
            }
        }

        @Override
        public boolean ranked() {
            return true;
        }
    }

    record Unknown(String mapName) implements FAFMap {
        @Override
        public boolean ranked() {
            return false;
        }
    }

}
