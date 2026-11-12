package com.faforever.server.jackson;

import com.faforever.server.message.client.GPGMessage;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.jsontype.TypeDeserializer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

@Slf4j
public class GPGClientMessageDeserializer extends ValueDeserializer<GPGMessage.Client> {

    /**
     * GPGNet args are positional, so a message must have exactly as many args as its type has components. Jackson
     * cannot enforce this for us: it silently skips extra array elements for POJOs-as-arrays with a property-based
     * creator regardless of {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}, and
     * {@link DeserializationFeature#FAIL_ON_MISSING_CREATOR_PROPERTIES} would also apply to (and break) optional fields
     * of other messages.
     * <p>
     * The count is the record's component count, or for a type built by a single-arg {@link JsonCreator} from a
     * record (GameMods), that record's. -1 means unknown and skips the check.
     */
    private static final ClassValue<Integer> ARG_COUNTS = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            if (type.isRecord()) {
                return type.getRecordComponents().length;
            }
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) && method.isAnnotationPresent(JsonCreator.class)
                    && method.getParameterCount() == 1 && method.getParameterTypes()[0].isRecord()) {
                    return method.getParameterTypes()[0].getRecordComponents().length;
                }
            }
            return -1;
        }
    };

    @Override
    public GPGMessage.Client deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        return ctxt.readValue(p, GPGMessage.Unknown.class);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
                                      TypeDeserializer typeDeserializer) throws JacksonException {
        // The external type id arrives merged with the value: [command, args]
        JsonNode rootNode = ctxt.readTree(p);
        String command = rootNode.get(0).asString();

        JavaType type;
        try {
            type = typeDeserializer.getTypeIdResolver().typeFromId(ctxt, command);
        } catch (InvalidTypeIdException e) {
            return ctxt.readTreeAsValue(rootNode, GPGMessage.Unknown.class);
        }
        // typeFromId returns null rather than throwing for an id with no registered subtype
        if (type == null) {
            return ctxt.readTreeAsValue(rootNode, GPGMessage.Unknown.class);
        }

        JsonNode args = rootNode.get(1);
        int expectedArgs = ARG_COUNTS.get(type.getRawClass());
        if (expectedArgs >= 0 && args.size() != expectedArgs) {
            LOG.debug("Wrong number of args for GPG command {}: expected {}, got {}", command, expectedArgs,
                      args.size());
            return ctxt.readTreeAsValue(rootNode, GPGMessage.Invalid.class);
        }

        try {
            return ctxt.readTreeAsValue(args, type);
        } catch (MismatchedInputException | ValueInstantiationException e) {
            LOG.debug("Invalid args for GPG command {}: {}", command, e.getMessage());
            return ctxt.readTreeAsValue(rootNode, GPGMessage.Invalid.class);
        }
    }
}
