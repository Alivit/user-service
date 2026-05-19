package com.minispring.userservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, String.class, source -> {
            if (source == null) {
                return null;
            }
            String trimmed = source.trim();
            return trimmed.isEmpty() ? null : trimmed;
        });
    }

}
