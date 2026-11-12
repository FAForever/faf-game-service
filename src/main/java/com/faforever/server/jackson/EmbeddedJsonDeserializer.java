package com.faforever.server.jackson;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;

/**
 * Reads a JSON string whose content is itself a JSON document, into the annotated property's type. Used for
 * GPGNet args, which can only be ints or strings, so structured data arrives as JSON text.
 */
public class EmbeddedJsonDeserializer extends ValueDeserializer<Object> {

    private final @Nullable JavaType type;

    @SuppressWarnings("unused")
    public EmbeddedJsonDeserializer() {
        this(null);
    }

    private EmbeddedJsonDeserializer(@Nullable JavaType type) {
        this.type = type;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, @Nullable BeanProperty property) {
        if (property == null) {
            return this;
        }
        return new EmbeddedJsonDeserializer(property.getType());
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        if (type == null) {
            return ctxt.reportBadDefinition(Object.class,
                                            "EmbeddedJsonDeserializer must be used on a property, to know its type");
        }
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            return ctxt.handleUnexpectedToken(type, p);
        }

        String json = p.getString();
        try (JsonParser embedded = ctxt.createParser(json)) {
            embedded.nextToken();
            return ctxt.readValue(embedded, type);
        } catch (StreamReadException e) {
            // Malformed JSON text is bad input, so report it as such rather than as a stream failure
            throw ctxt.weirdStringException(json, type.getRawClass(), "not valid JSON: " + e.getOriginalMessage());
        }
    }
}
