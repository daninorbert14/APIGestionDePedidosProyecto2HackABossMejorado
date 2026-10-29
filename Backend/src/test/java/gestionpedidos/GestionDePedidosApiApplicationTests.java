package gestionpedidos;

import gestionpedidos.config.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Smoke test: garantiza que la aplicación entera arranca. Su valor está en la
// combinación de las tres anotaciones, no en el método vacío:
// - @ActiveProfiles("test"): perfil de tests (Flyway desactivado, ddl-auto=create)
//   para que el contexto no intente migrar ni validar contra un esquema ajeno.
// - @Import(PostgresTestcontainersConfig): PostgreSQL real vía Testcontainers;
//   es la misma combinación que repiten el resto de tests de contexto.
// Trampa documentada: si se usa @ContextConfiguration en lugar de @Import, se
// sustituye la configuración de la aplicación y el test pasaría en vacío.
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
class GestionDePedidosApiApplicationTests {

    @Test
    void contextLoads() {
    }
}