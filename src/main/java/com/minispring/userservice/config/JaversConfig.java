package com.minispring.userservice.config;

import lombok.Getter;
import lombok.Setter;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JaversConfig {

    @Bean
    public Javers javers() {
        return JaversBuilder.javers().build();
    }

    @Bean
    @ConfigurationProperties(prefix = "app.audit")
    public AuditProperties auditProperties() {
        return new AuditProperties();
    }

    @Getter
    @Setter
    public static class AuditProperties {
        private boolean enabled = true;
    }
}
