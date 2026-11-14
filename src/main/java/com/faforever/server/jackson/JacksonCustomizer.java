package com.faforever.server.jackson;

import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import jakarta.inject.Singleton;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class JacksonCustomizer implements JsonMapperBuilderCustomizer {
    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.propertyNamingStrategy(new PropertyNamingStrategies.SnakeCaseStrategy());
        builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES);
        builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }
}
