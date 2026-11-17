# MEMORY.md — Gestión de Pedidos (Backend + Frontend)

Memoria del proyecto entre sesiones (máximo ~50 líneas): resume o elimina lo que ya no aporte.

## Estado actual

- Backend completo: CRUD Categoría/Producto/Terminal, ciclo de vida de Pedido, Estadísticas (productos más vendidos, ranking terminales). Tests backend en verde: **124** (`mvn -f Backend/pom.xml test`), con Testcontainers contra PostgreSQL real y `MigracionFlywayTest`. Frontend: flujo Terminal/Cocina/Recogida y "Los más vendidos" funcionando; sin tests de UI.
- Specs cerradas: **004** (PostgreSQL + Flyway V1/V2 + Testcontainers), **005 Docker** (T1-T14; T5-T7 y T12-T13 los ejecuta el usuario con Docker Desktop; cierra el cambio RF-22) y **006 CI** (T1-T7 en verde en local; pendiente primera ejecución real con el usuario, ver "CI" más abajo; README con la insignia). En curso **007 Dependabot**: T1-T7 hechas.

## Decisiones (y por qué)

- Catálogo (CRUD producto/categoría/terminal) vive en backend/Swagger, no en frontend — separación backoffice vs. operativo.
- DTOs siempre clase Lombok, nunca `record` ni MapStruct — coherencia con el resto del proyecto.
- `listarProductos` sigue en memoria (streams), no `Specification` — rehacer tests ya verificados no compensaba; mejora opcional.
- Paquete simplificado a `gestionpedidos` (sin `com.empresa`) — proyecto de portfolio, no librería a publicar.
- Duplicados de catálogo insensibles a mayúsculas (`existsByNombreIgnoreCase`); el texto se guarda y muestra tal como se escribió.

### CI — spec 006

- **Workflow único con 4 jobs** (`backend`, `frontend`, `docker` en paralelo → `publish` con `needs`): la publicación debe pasar por las comprobaciones de esa misma ejecución (RF-16) con las mismas imágenes, sin reconstruir (RF-19); dos workflows ligados con `workflow_run` añadían carreras y se descartaron. **Publicación vía artefacto**: `docker` construye con `push: false` y sube un tar solo en push a main; `publish` es el único job con `packages: write` y lo descarga (mismas imágenes, sin reconstruir). En PRs no se sube nada.
- **Cancelación solo en PRs**: `cancel-in-progress: ${{ github.event_name == 'pull_request' }}`; main nunca se cancela porque puede estar publicando (RF-25).
- `latest` condicional: en el instante de publicar se compara la punta de `main` (`git ls-remote`) con `github.sha`; re-ejecutar un commit antiguo republica su etiqueta sin retroceder `latest`. Prefijo de imagen en minúsculas con `tr` (GHCR rechaza mayúsculas y así funciona en forks).

### Dependabot — spec 007

- **Decisión** (`.github/dependabot.yml`): 5 ecosistemas en 6 entradas (`npm` en `/Frontend`, `maven` en `/Backend`, `github-actions` en `/`, `docker` en cada Dockerfile y `docker-compose` en la raíz), semanal los lunes 09:00 UTC, máximo 3 PRs abiertos por entrada, un PR por dependencia (sin `groups`) y `cooldown` de 7 días (las de seguridad no se retrasan; si «Check for updates» rechaza `cooldown` en algún ecosistema, se retira solo en esa entrada y se comenta aquí). Ignores con motivo en el propio archivo (RF-9): `postgres` completo en las tres entradas de imagen porque la etiqueta debe seguir coincidiendo con la de las pruebas con Testcontainers (RF-19 de la spec 005); y solo los mayores de `node` (Node 22), `eclipse-temurin` y `maven` (Java 17), versiones fijadas por el proyecto. Ninguno para ESLint ni minimatch (RF-8): esperar la versión corregida (README, Limitaciones conocidas).
- **PR con CI rojo (RF-10)**: no se fusiona: se revisa y se arregla en su propia rama, o se cierra con el comentario de Dependabot que ignora esa dependencia o esa versión mayor (p. ej. `@dependabot ignore this major version`) para que no se reabra con cada versión nueva.
- **Alertas de seguridad (RF-12)**: activar las alertas y las actualizaciones de seguridad del repositorio es un paso manual e independiente del archivo de configuración; en un fork vienen desactivadas.

### Docker — cambio RF-22 (spec 005)

- Capa `RUN mvn -B dependency:go-offline` en `Backend/Dockerfile` (entre `COPY pom.xml` y `COPY src`): cambiar solo `src/` no re-descarga dependencias. No se usó `--mount=type=cache`: BuildKit no exporta monturas a la caché remota (en CI re-descargaría siempre) y, sobre `/root/.m2`, sombrearía la capa. El `cache-to` del backend lleva **`mode=max` comentado** en el workflow: sin esa capa intermedia de dependencias la caché Maven deja de funcionar sin aviso (evita que alguien lo "simplifique" a `mode=min`).
- Jar en capas (conclusión T10): mejora opcional futura; no se implanta (alteraría el arranque de la imagen de ejecución).

## Aprendizajes y errores a evitar

- El backend de Docker en el puerto 8080 usa `FRONTEND_URL=http://localhost:8081`: desde el dev en 5173 todo falla por CORS ("No Access-Control-Allow-Origin"). Diagnosticar abriendo el endpoint en una pestaña (la navegación no aplica CORS) y probando el frontend del 8081; solución: `docker compose stop backend` y backend del IDE en el 8080.
- Tras una acción que cambia datos, llamar a `refetch()` en el mismo onClick (patrón de CocinaPage); fiarse solo del polling de 15 s deja la vista desactualizada hasta el siguiente tick.
- Validar el YAML del workflow con actionlint vía Docker (`docker run --rm -v "$PWD":/repo --workdir /repo rhysd/actionlint:latest -color`): no hay PyYAML instalado para el validador de Python.
- El token de publicación va en `env:` del paso y se usa como `"$GITHUB_TOKEN"` en el script; **nunca** `${{ secrets.GITHUB_TOKEN }}` interpolado dentro de un `run` (se filtraría en el log). **No hacer `docker push` manual a ghcr.io antes de la primera publicación del CI** (advertido en README): los paquetes se crearían fuera del flujo, sin pasar las comprobaciones, y se condiciona la primera ejecución.

### Tests con Testcontainers (arreglo T9)

- En clase de test funciona `@Import(PostgresTestcontainersConfig.class)` (lo procesa `ImportsContextCustomizer`); `@ContextConfiguration(classes=...)` con una clase que NO es `@TestConfiguration` sustituye la config de la app → `contextLoads` pasa vacío (falso verde). No hardcodear `spring.datasource.url/username/password` en `application-test.properties`: pisa a `@ServiceConnection` y el contenedor publica un puerto aleatorio → "Connection refused". En tests: `spring.flyway.enabled=false` con `ddl-auto=create`.
- Testcontainers 2.x renombró artefactos (`postgresql` → `testcontainers-postgresql`); el BOM de Spring Boot solo gestiona los nuevos → nunca mezclar módulos 1.x con core 2.x.
- `MotorPostgresqlValidator` captura `IllegalArgumentException` de `${DB_URL}` sin definir: no debe romper el arranque. Migraciones probadas aparte (`MigracionFlywayTest`): habilitar Flyway en `application-test.properties` rompería los tests (V2 siembra catálogo con nombres únicos).

## Próximos pasos

- Opcionales: migrar `listarProductos` a `Specification`; jar en capas (`-Djarmode=tools`); verificación visual en navegador de la limpieza de Recogida (hook `usePedidoPorCodigo`, bloque CSS `.pedido-encontrado-card`, clase `pedido-listo-card--sin-accion`), sin cambios visuales por diseño pero no revisada con backend arrancado.
