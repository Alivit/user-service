package com.minispring.userservice;

import com.minispring.userservice.client.AuthGrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @MockitoBean
    protected AuthGrpcClient authGrpcClient;

    @MockitoBean
    protected OAuth2AuthorizedClientManager authorizedClientManager;

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    @ServiceConnection(name = "redis")
    static GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    static {
        postgreSQLContainer.start();
        redisContainer.start();
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(jwtDecoder, authGrpcClient);
    }
}
