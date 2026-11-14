package com.faforever.server.message.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;
import tools.jackson.databind.node.ValueNode;

sealed public interface OptionValue {

    int intValue();

    String strValue();

    record Str(String strValue) implements OptionValue {
        @Override
        public int intValue() {
            return Integer.parseInt(strValue());
        }
    }

    record Int(int intValue) implements OptionValue {

        @Override
        public String strValue() {
            return Integer.toString(intValue);
        }
    }

    @JsonCreator
    static OptionValue fromValue(ValueNode value) {
        return switch (value) {
            case StringNode stringNode -> new Str(stringNode.stringValue());
            case IntNode intNode -> new Int(intNode.intValue());
            default -> throw new IllegalStateException("Unknown option value");
        };
    }
}
