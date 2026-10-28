package gestionpedidos.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false) 
public class PostgresTestcontainersConfig {

    // @ServiceConnection hace que Spring Boot configure solo la URL, el usuario y la contraseña
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");   // misma etiqueta que docker-compose.yml
    }
}