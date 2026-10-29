package gestionpedidos.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

// @TestConfiguration (y no @Configuration): solo la importan los tests que la
// necesitan con @Import; fuera de ellos no hay contenedor. Trampa: si se usa
// @ContextConfiguration en su lugar, sustituye la config de la aplicación y
// los tests pasan en vacío (falso verde).
// proxyBeanMethods=false: el bean no invoca a otros del contexto, se ahorra
// el proxy CGLIB sin perder nada.
@TestConfiguration(proxyBeanMethods = false) 
public class PostgresTestcontainersConfig {

    // @ServiceConnection hace que Spring Boot configure solo la URL, el usuario y la contraseña.
    // Es la vía correcta: fijar spring.datasource.* a mano en application-test.properties
    // pisa estos valores y el contenedor (puerto aleatorio) queda inalcanzable → "Connection refused".
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");   // misma etiqueta que docker-compose.yml: una versión distinta entre tests y producción es fuente de "pasa en CI, falla en local"
    }
}
