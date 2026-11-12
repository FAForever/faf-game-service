package com.faforever.server.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.Arrays;
import java.util.List;

public class CommaSeparatedIntDeserializer extends ValueDeserializer<List<Integer>> {

    @Override
    public List<Integer> deserialize(JsonParser p, DeserializationContext ctxt) {
        String value = p.getString();
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        try {
            return Arrays.stream(value.split(",")).map(String::trim).map(Integer::valueOf).toList();
        } catch (NumberFormatException e) {
            throw ctxt.weirdStringException(value, List.class, "not a comma separated list of integers");
        }
    }
}
