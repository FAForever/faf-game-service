package com.faforever.server.message.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public record KeyValue(
        String key,
        OptionValue value
) {}
