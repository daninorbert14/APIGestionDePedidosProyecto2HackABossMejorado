package gestionpedidos;

import gestionpedidos.config.PostgresTestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de migraciones (hueco cerrado de la spec 004, RF-1/RF-6/RF-13).
 *
 * Arranca la aplicación completa contra un contenedor PostgreSQL VACÍO con el mismo
 * pipeline de producción: Flyway aplica V1+V2 y Hibernate valida el esquema
 * (ddl-auto=validate). Si una migración está rota o el esquema queda desincronizado
 * de las entidades, el contexto no arranca y este test falla.
 *
 * No puede reutilizar el perfil "test" tal cual: application-test.properties desactiva
 * Flyway y usa ddl-auto=create porque la V2 siembra el catálogo (IDs y nombres fijos,
 * con UNIQUE) y chocaría con los datos que crean los propios tests. Por eso aquí se
 * reactivan flyway y validate con @SpringBootTest(properties=...): las propiedades
 * inline tienen prioridad sobre application-test.properties, sin tocar ese fichero.
 * Al ser un contexto con clave distinta, Testcontainers levanta un contenedor nuevo
 * y vacío: los datos de otros tests no pueden interferir.
 */
@SpringBootTest(
        properties = {
                "spring.flyway.enabled=true",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
@ActiveProfiles("test")
@Import(PostgresTestcontainersConfig.class)
class MigracionFlywayTest {

    /* El bean Flyway solo existe porque spring.flyway.enabled=true en este contexto.
     * info().applied() devuelve las migraciones que constan en flyway_schema_history */
    @Autowired
    private Flyway flyway;

    /* Para comprobar los datos que siembra la V2 sin pasar por la API REST */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aplicaLasMigracionesV1YV2SobreUnaBaseVacia() {
        // El simple hecho de que el contexto haya arrancado ya exige que Flyway
        // migrara V1+V2 y que Hibernate validara el esquema contra las entidades.
        // Aquí lo hacemos explícito: deben constar exactamente V1 y V2 como aplicadas.
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2");
    }

    @Test
    void laV2SiembraElCatalogoEsperado() {
        // V2__catalogo_ejemplo.sql inserta 3 terminales, 7 categorías y 30 productos
        // con IDs explícitos, y ningún pedido. Si V2 se aplicara a medias o fallara
        // un INSERT a mitad de fichero, los conteos no cuadrarían.
        assertThat(contarFilas("terminales")).isEqualTo(3);
        assertThat(contarFilas("categorias")).isEqualTo(7);
        assertThat(contarFilas("productos")).isEqualTo(30);
        assertThat(contarFilas("pedidos")).isEqualTo(0);
    }

    private long contarFilas(String tabla) {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tabla, Long.class);
        return total != null ? total : 0;
    }
}
