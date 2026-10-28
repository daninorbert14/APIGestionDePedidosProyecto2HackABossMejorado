package gestionpedidos;

import gestionpedidos.config.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
class GestionDePedidosApiApplicationTests {

    @Test
    void contextLoads() {
    }
}