package com.minispring.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule trimCustomizer() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new ValueDeserializer<>() {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
                String value = p.getValueAsString();
                if (value == null) {
                    return null;
                }
                String str = value.trim();
                return str.isEmpty() ? null : str;
            }
        });
        return module;
    }
}
