package com.faforever.server.jackson;

import com.faforever.server.message.client.dto.ResultDetails;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.List;

public class ResultDetailsDeserializer extends ValueDeserializer<ResultDetails> {

    @Override
    public ResultDetails deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() != JsonToken.VALUE_STRING) {
            ctxt.handleUnexpectedToken(ResultDetails.class, p);
        }

        List<String> parts = List.of(p.getString().split(" "));
        if (parts.size() < 2) {
            throw ctxt.weirdStringException(p.getString(), List.class,
                                            "GameResults should contain a list of a at least two strings delimited by a space");
        }

        int score;
        try {
            score = Integer.parseInt(parts.getLast());
        } catch (NumberFormatException e) {
            throw ctxt.weirdStringException(p.getString(), List.class, "Last element representing score is not an int");
        }

        ResultDetails.Type resultType = ResultDetails.Type.of(parts.get(parts.size() - 2));
        List<String> metadata = parts.subList(0, parts.size() - 2);
        return new ResultDetails(resultType, score, metadata);
    }
}
