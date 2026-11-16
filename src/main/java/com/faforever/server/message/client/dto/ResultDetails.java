package com.faforever.server.message.client.dto;

import com.faforever.server.message.client.GameOption;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;
import java.util.Locale;

public record ResultDetails(
        Type resultType,
        int score,
        List<String> metadata
) {
    public ResultDetails {
        metadata = List.copyOf(metadata);
    }

    public sealed interface Type {

        enum Known implements Type {
            VICTORY, DEFEAT, DRAW, MUTUAL_DRAW
        }

        record Unknown(String value) implements Type {}

        static Type of(String value) {
            try {
                return Known.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException _) {
                return new Unknown(value);
            }
        }
    }
}
