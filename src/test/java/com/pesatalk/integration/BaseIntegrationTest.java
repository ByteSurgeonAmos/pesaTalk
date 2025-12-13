package com.pesatalk.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:16-alpine")
    )
        .withDatabaseName("pesatalk_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
        DockerImageName.parse("redis:7-alpine")
    )
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Test-specific configs
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Mock external services
        registry.add("whatsapp.api.verify-token", () -> "test-verify-token");
        registry.add("whatsapp.api.app-secret", () -> "test-app-secret");
        registry.add("whatsapp.api.access-token", () -> "test-access-token");
        registry.add("whatsapp.api.phone-number-id", () -> "123456789");

        registry.add("mpesa.api.consumer-key", () -> "test-consumer-key");
        registry.add("mpesa.api.consumer-secret", () -> "test-consumer-secret");
        registry.add("mpesa.api.passkey", () -> "test-passkey");
        registry.add("mpesa.api.shortcode", () -> "174379");
        registry.add("mpesa.api.callback-url", () -> "https://test.com");

        registry.add("encryption.phone-key", () -> "0123456789abcdef0123456789abcdef");
    }
}
