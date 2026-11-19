# Tareas 008 — Seguridad: resource server JWT

Espec: `specs/008-spring-security/spec.md`. Plan: `specs/008-spring-security/plan.md`. Orden por dependencias (backend primero; nada de frontend en esta spec). Cada tarea, 20–30 min.

**Nota previa (RF-46)**: T1 solo arranca si el usuario aprueba las tres dependencias (`spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-security-test`).

## T1 — Dependencias, configuración JWT y ajuste de los tests existentes
**Backend** · RF-4 (parcial), RF-36, RF-37, RF-38, RF-41, RF-46, RF-47 (parcial)
Añadir al `Backend/pom.xml` las tres dependencias de RF-46 sin versiones; en `application.properties` poner `spring.security.oauth2.resourceserver.jwt.issuer-uri=${JWT_ISSUER_URI}` y `...jwk-set-uri=${JWT_JWK_SET_URI}` sin defecto; en `src/test/resources/application-test.properties` sus valores ficticios; crear `SeguridadTestConfig` (`@TestConfiguration` con `JwtDecoder` simulado: token de prueba → rol ADMIN, otro → `JwtException`); anotar las 5 clases `@WebMvcTest` con `@AutoConfigureMockMvc(addFilters = false)`; importar el decoder en `PedidoIntegrationTest` y añadirle la cabecera `Authorization: Bearer <token ADMIN>` a sus peticiones.
**Hecho cuando:** `mvn -B -f Backend/pom.xml test` pasa con los 124 tests existentes (incluidos los 54 de controlador sin token y `PedidoIntegrationTest` con token), sin red y sin variables de entorno nuevas.

## T2 — Cadena de seguridad: JWT, matriz de rutas y errores 401/403
**Backend** · RF-1, RF-2, RF-3, RF-8, RF-9, RF-10 (parcial), RF-17, RF-18, RF-19 (parcial), RF-20, RF-21, RF-22, RF-23, RF-24, RF-25, RF-29, RF-30, RF-31, RF-32 (parcial), RF-47, RF-48
Crear `SecurityConfig` (`SecurityFilterChain` stateless con CSRF off, CORS integrado, `oauth2ResourceServer` JWT, matriz de §5.1 del plan con `anyRequest().denyAll()`), `ConverterRolesJwt` (único punto `realm_access.roles` → `ROLE_*`), `EntryPointNoAutenticado` (401 de RF-2 + `WWW-Authenticate: Bearer`), `ManejadorAccesoDenegado` (403 de RF-31) y `OpenApiConfig` (esquema Bearer JWT para el botón «Authorize»).
**Hecho cuando:** arrancando el backend en local, un GET público sin token da 401 con `{"Error: ": "No autenticado: token ausente, caducado o inválido"}`, el mismo con rol equivocado da 403 con `{"Error: ": "No autorizado: el rol del usuario no permite esta operación"}` y una ruta inexistente da 401 sin token y 403 con token.

## T3 — Política de transición y manejo del AccessDeniedException del método
**Backend** · RF-26, RF-27, RF-28, RF-32 (transición), RF-33, RF-35 (parcial)
Crear `PoliticaTransicionEstado` (fail-closed: ADMIN → cualquier destino; COCINA → PREPARACION/LISTO; RECOGIDA → PAGADO/ENTREGADO; destino nulo o sin rol → false), anotar `PedidoController.cambiarEstadoDelPedido` con `@PreAuthorize("@politicaTransicionEstado.permite(#dto.estado, authentication)")` y añadir en `GlobalExceptionHandler` el `@ExceptionHandler(AccessDeniedException.class)` → 403 con el cuerpo de RF-27, antes del catch-all. Sin tocar `PedidoService`, `dto` ni sus mensajes.
**Hecho cuando:** `git diff` en `service/`, `dto/`, `repository/`, `model/` y `PedidoServiceTest` está vacío, y `mvn test` sigue en verde; un `AccessDeniedException` de la política devuelve 403 con `{"Error: ": "No autorizado: el rol del usuario no permite esta transición de estado"}` y jamás 500.

## T4 — Tests de seguridad por ruta (endpoint × rol)
**Backend** · RF-17, RF-18, RF-19 (parcial), RF-20, RF-21, RF-22, RF-23, RF-24, RF-25, RF-29, RF-30, RF-31, RF-32, RF-39, RF-48
Crear `Backend/src/test/java/gestionpedidos/seguridad/SeguridadRutasTest.java` (`@WebMvcTest` + `@Import(SecurityConfig…)` + `@MockitoBean JwtDecoder` + `jwt()`): por cada fila de la matriz 200 con rol permitido / 403 con rol no permitido / 401 sin token; cuerpos exactos de RF-2 (con cabecera `WWW-Authenticate`) y RF-31; ruta inexistente 401/403; token sin rol o rol desconocido; token inválido → 401; preflight `OPTIONS` sin token; `/v3/api-docs` sin 401/403 en el slice.
**Hecho cuando:** `mvn -B -f Backend/pom.xml test` pasa los tests nuevos y los 54 de controlador siguen intactos.

## T5 — Tests de seguridad de transiciones y precedencia
**Backend** · RF-26, RF-27, RF-34, RF-39, RF-43
Crear `Backend/src/test/java/gestionpedidos/seguridad/SeguridadTransicionesTest.java`: matriz estado destino × rol (403 RF-27 y 403 RF-31 para TERMINAL), doble fallo → 403 y no 400, salto de orden por rol que cubre el destino → 400 del servicio, cuerpo nulo (ADMIN → 400; COCINA/RECOGIDA → 403), precedencia 401 → 403 de ruta → 400 de parseo → 403 de política → 400/404, y RF-34 (política simulada que deniega → 403 y no 500).
**Hecho cuando:** `mvn -B -f Backend/pom.xml test` queda en verde con los tests nuevos; COCINA `CREADO→PAGADO` con JSON válido da 403 (y no 400) y TERMINAL con JSON mal formado da 403 de RF-31 (y no 400).

## T6 — Keycloak en Docker, variables del entorno y ejemplo de entorno
**Backend (docker-compose.yml y .env.example)** · RF-4 (parcial), RF-5, RF-6, RF-15, RF-44, RF-45, RF-51
Añadir el servicio `keycloak` a `docker-compose.yml` (imagen `keycloak/keycloak:26.8.2`, `127.0.0.1:8180:8180`, `KC_HOSTNAME=http://localhost:8180`, `--import-realm`, `KC_BOOTSTRAP_ADMIN_PASSWORD` con `:?`), el volumen del realm, y las variables `JWT_ISSUER_URI`/`JWT_JWK_SET_URI` del `backend` con defectos locales (`:-`); en `.env.example`, `KEYCLOAK_ADMIN_PASSWORD=` vacía + `KEYCLOAK_ADMIN_USERNAME`, `JWT_ISSUER_URI` y `JWT_JWK_SET_URI` documentadas por nombre sin valores reales.
**Hecho cuando:** `docker compose config` falla sin `KEYCLOAK_ADMIN_PASSWORD` y sin `DB_PASSWORD`, y pasa con ambas; `docker compose config --quiet` con solo `DB_PASSWORD` y `KEYCLOAK_ADMIN_PASSWORD` ficticias sigue sin quejarse (las JWT tienen defecto local).

## T7 — Realm versionado y verificación funcional del proveedor
**Backend (keycloak/realm-gestion-pedidos.json)** · RF-7, RF-10, RF-11, RF-12, RF-13, RF-14, RF-16
Crear `keycloak/realm-gestion-pedidos.json`: roles `TERMINAL/COCINA/RECOGIDA/ADMIN`, usuarios demo por rol con contraseñas ficticias, cliente `frontend-app` público con PKCE S256 (`pkce.method=S256`, comprobado tras arrancar) y `redirectUris` con comodín + `webOrigins` sin ruta (`http://localhost:5173`, `http://localhost:8081`), `accessTokenLifespan` 300 s, cliente `dev-test` con direct access grant, sin secretos.
**Hecho cuando:** `docker compose up` importa el realm sin errores; `curl` al endpoint de token con el cliente `dev-test` devuelve un token cuyo `iss` es `http://localhost:8180/realms/gestion-pedidos`; el preflight con `Origin: http://localhost:5173` devuelve ese origen en `access-control-allow-origin`; con un token de cada rol, los 200/403/401 de §5.1 coinciden con la spec y sus cuerpos son idénticos a la spec (RF-16).

## T8 — CI y Dependabot
**Backend (CI)** · RF-41, RF-44, RF-50
En `.github/workflows/ci.yml`, job `docker`: paso `KEYCLOAK_ADMIN_PASSWORD: ci-dummy` en «Validar Compose» y gemelo negativo sin `KEYCLOAK_ADMIN_PASSWORD` que debe fallar; en `.github/dependabot.yml`, entrada `docker-compose` con ignore `version-update:semver-major` de `keycloak/keycloak` y comentario en español.
**Hecho cuando:** los tres `docker compose config` del job dan el resultado esperado (2 verdes, 2 rojos previstos) y Dependabot deja de proponer saltos de mayor de `keycloak/keycloak` (verificado con «Check for updates» tras fusionar).

## T9 — Documentación de seguridad (AGENTS.md, README raíz, Backend/README, Postman)
**Documentación** · RF-14, RF-17, RF-27, RF-44, RF-49, RF-51
Corregir `AGENTS.md` línea 11 («ruta inexistente → 404» → 401 sin token / 403 con token; el 409 sigue para duplicados, la transición pasa a 400), sección Entorno (JWT por nombre) y sección Docker (dos contraseñas obligatorias, puerto 8180, «cambiar el puerto del frontend exige editar el JSON del realm»); en `README.md`, Configuración con las dos contraseñas, las variables JWT por nombre, ejemplo exacto de curl con token y nota de estado intermedio (frontend en 401 hasta la 009); en `Backend/README.md`, fila nueva de ruta inexistente 401/403 y `Backend/README.md:263` pasa de 409 a 400; añadir la carpeta «Seguridad» a la colección Postman.
**Hecho cuando:** un lector nuevo sabe obtener un token, llamar a la API con él y distinguir 401/403/400/404/409 leyendo solo README raíz + Backend/README, y ninguna línea documenta la ruta inexistente como 404.

## T10 — Verificación final, estado intermedio y memoria
**Documentación** · RF-40, RF-42, RF-49, RF-50
Ejecutar `mvn -B -f Backend/pom.xml test` (recuento completo en verde), revisar que ningún fichero de `src/main` apaga la seguridad (RF-40) ni hay cambios en `service/`/`dto/`, confirmar en local el flujo completo de T7 y el estado intermedio del frontend (401 hasta la 009), y actualizar `MEMORY.md` (dependencias aprobadas, versión fija de Keycloak + ignore de Dependabot, aprendizajes) con ≤ ~50 líneas.
**Hecho cuando:** `mvn test` está en verde con 124 + tests nuevos, el grep de `src/main` no encuentra perfiles ni propiedades que desactiven la seguridad, y `MEMORY.md` refleja el estado final sin superar ~50 líneas.
