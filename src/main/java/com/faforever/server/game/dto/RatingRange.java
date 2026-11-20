package com.faforever.server.game.dto;

import org.jspecify.annotations.Nullable;

public record RatingRange(boolean enforce, @Nullable Integer min, @Nullable Integer max) {

    public boolean inRange(double rating) {
        return (min() == null || rating >= min()) && (max() == null || rating <= max());
    }

}
