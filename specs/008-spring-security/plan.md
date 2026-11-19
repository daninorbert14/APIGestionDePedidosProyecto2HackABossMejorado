# Plan 008 — Seguridad: backend como resource server JWT con roles TERMINAL, COCINA, RECOGIDA y ADMIN

Spec: `specs/008-spring-security/spec.md` (Estado: aprobada). Este plan describe el **cómo**; el qué está en la spec. Es una spec de **backend + infraestructura de desarrollo + CI + documentación**: no se toca `Frontend/` (el login es la spec 009) ni la capa de negocio (`model`, `repository`, `service` quedan intactos; el servicio no cambia de firma, RF-28).

> **⚠️ DECISIÓN QUE DEBE APROBAR EL USUARIO («⚠️ Pregunta antes» de AGENTS.md: añadir una dependencia)**: RF-46 introduce **exactamente tres dependencias nuevas** en `Backend/pom.xml`: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` (ambas de compilación) y `spring-security-test` (de test). La spec ya condiciona su incorporación a la aprobación explícita del usuario: **no se ejecuta T1 hasta que apruebe las tres**. No se añade ninguna otra dependencia; si apareciera una cuarta, se para y se pide aprobación (RF-46).
> Ningún otro punto del plan cae en el «⚠️ Pregunta antes»: **ningún DTO que consume el frontend cambia** (`EstadoPedidoRequestDto` se queda igual), no se toca `data.sql` (no existe) ni el esquema de ninguna entidad.

## 1. Enfoque técnico

Cuatro frentes, en este orden de dependencia:

1. **Cadena de seguridad en el backend** (Spring Security 7 sobre Spring Boot 4.1.2): un `SecurityFilterChain` en `gestionpedidos/config/SecurityConfig.java` que apaga CSRF y sesiones (stateless, RF-47), integra el CORS existente (RF-48), autentica Bearer JWT contra el proveedor (RF-1, RF-2, RF-3), autoriza **por ruta** según la matriz (RF-17 a RF-25, RF-29, RF-30) con **denegado por defecto** y traduce los errores 401/403 a los cuerpos exactos de la spec (RF-31, RF-32). La única comprobación que no puede vivir en la ruta —la transición por **estado destino** (RF-26)— vive en **seguridad a nivel de método** sobre el controlador, apoyada en un componente de política (`PoliticaTransicionEstado`), por delante del servicio (RF-28) y fail-closed (RF-26).
2. **Pruebas**: los 54 tests de controlador existentes se quedan sin token desactivando los filtros **en el propio test** (RF-36); los 5 de contexto/integración/migración arrancan con propiedades ficticias y/o un decodificador simulado de solo test (RF-37), siendo `PedidoIntegrationTest` el único cuyo comportamiento cambia (token ADMIN, RF-38); los tests nuevos de seguridad usan `jwt()` de `spring-security-test` (RF-39, RF-41) y la suite completa queda en verde sin red (RF-42).
3. **Keycloak en desarrollo local**: servicio nuevo en `docker-compose.yml` con imagen de **versión fija**, puerto **8180** solo en `127.0.0.1`, realm importado desde un JSON versionado (RF-5, RF-45, RF-7) y la contraseña del administrador obligatoria con el mismo mecanismo que `DB_PASSWORD` (RF-44). El JSON del realm fija ya el contrato de la spec 009: cliente público con PKCE S256, redirect URIs con comodín, **web origins sin ruta** y `accessTokenLifespan` 300 s (RF-11, RF-12, RF-13).
4. **CI y documentación**: el job `docker` gana el paso negativo gemelo y el valor ficticio de `KEYCLOAK_ADMIN_PASSWORD` (RF-41, RF-44); Dependabot ignora los mayores de la imagen de Keycloak (RF-50); y se corrigen/actualizan AGENTS.md, README raíz, Backend/README, `.env.example`, la colección Postman y `MEMORY.md` (RF-49, RF-51).

## 2. Decisiones sobre las dudas abiertas de la spec

### 2.1 La política de transición no se carga en los slices de los 54 tests (RF-36, RF-40)

**Decisión**: `SecurityConfig` (que contiene el `SecurityFilterChain`, `@EnableMethodSecurity` y el `CorsConfigurationSource`) es una clase `@Configuration` normal y `PoliticaTransicionEstado` es un `@Component` normal; **ninguno de los dos se carga en un `@WebMvcTest`**, porque el `WebMvcTypeExcludeFilter` de Spring Boot 4.1 solo incluye en el escaneo `@Controller`, `@ControllerAdvice`, `Filter`, `SecurityFilterChain` **como bean declarado en una clase ya incluida**, `WebMvcConfigurer`, convertidores, etc. — no `@Configuration` ni `@Component` corrientes (verificado en el Javadoc de `@WebMvcTest` 4.1.2). Como en esos slices **no se activa `@EnableMethodSecurity`**, la anotación `@PreAuthorize` del controlador queda **inerte** y el bean de política **no hace falta**: los 54 tests siguen llamando al servicio mockeado igual que hoy. Se comprueba ejecutando la suite: si alguno fallara con «No bean named 'politicaTransicionEstado'», es que el slice cargó la seguridad y habría que revisar que `@EnableMethodSecurity` sigue viviendo solo en `SecurityConfig`.
**Descartadas**: (a) desactivar la seguridad por perfil o propiedad de configuración — lo prohíbe RF-40; (b) un bean de política permisivo en `src/main` — también lo prohíbe RF-40; (c) cargar la política en los slices con un bean permisivo — innecesario (no se necesita) y, si alguna vez fuese necesario, **solo existirá en `src/test`**, nunca en `src/main` (así lo fija RF-36); (d) no usar seguridad a nivel de método y comprobar el rol en el servicio — lo descarta RF-28 (cambiaría la firma y duplicaría la evaluación).

### 2.2 Versión fija de Keycloak (RF-45) y compatibilidad con web origins sin ruta (RF-12)

**Decisión**: imagen **`keycloak/keycloak:26.8.2`** (etiqueta de versión fija de parche, verificada en Docker Hub en noviembre de 2026: la serie activa es 26.8 y `26.8.2` es su último parche; la propia documentación de Keycloak ya ofrece la guía de hostname con la versión 26.8.2 seleccionada). Keycloak representa los `webOrigins` como **origin** (`esquema://host[:puerto]`, sin ruta): el navegador envía el header `Origin` sin ruta, de modo que `http://localhost:5173` y `http://localhost:8081` son la forma válida, y conviven sin conflicto con redirect URIs que sí llevan comodín `/*`. La compatibilidad se **verifica** además de por lectura del JSON (T7): importación del realm sin errores + comprobación funcional del preflight (ver 2.5).
**Descartadas**: etiqueta `latest` o el tag menor `26.8` (no fijan versión, RF-45 exige fija, y `26.8` se repinta); `nightly` (no es estable); otra imagen/registro distinto sin necesidad.

### 2.3 API de Spring Security 7 con Spring Boot 4.1 (RF-46)

**Decisión**: usar **las versiones que ya gestiona el BOM de Spring Boot 4.1.2**: `Backend/pom.xml` añade los starters **sin `<version>`** y el BOM aporta **Spring Security 7.1.2** (`spring-security.version` en `spring-boot-dependencies-4.1.2.pom`, comprobado en el repositorio local) y `spring-security-test` 7.x. La API es la de Security 7: DSL lambda (`csrf(AbstractHttpConfigurer::disable)`, `sessionManagement(...STATELESS)`, `authorizeHttpRequests(...)`, `oauth2ResourceServer(...)`), `@EnableMethodSecurity` y un `Converter<Jwt, Collection<GrantedAuthority>>` propio para los roles de realm (RF-9). Los tres starters nombrados en RF-46 existen con esos nombres exactos en el BOM de 4.1.2 (comprobado: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security-test`).
**Descartadas**: fijar versiones de Security a mano en el `pom.xml` (duplica el BOM y rompe en cada upgrade de Boot); usar `WebSecurityConfigurerAdapter` o `antMatchers` (eliminados en Security 6/7); starter `spring-boot-starter-security-test` en lugar de `spring-security-test` (RF-46 nombra `spring-security-test` y ese artefacto existe y está gestionado por el BOM).

### 2.4 Anotación exacta para desactivar filtros en Spring Boot 4.1 (RF-36)

**Decisión**: `@AutoConfigureMockMvc(addFilters = false)` en cada una de las 5 clases `@WebMvcTest`, con el import **`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`** (verificado en el Javadoc de Spring Boot 4.1.2: la anotación existe en ese paquete y el atributo `addFilters` existe con valor por defecto `true`; con `false` no se registra **ningún** filtro de la aplicación —incluido `springSecurityFilterChain`— en MockMvc, de modo que los 54 tests prueban contrato HTTP sin autorización).
**Descartadas**: `@AutoConfigureMockMvc` a secas (los filtros seguirían activos y todo devolvería 401); `@WithMockUser` (no salta las reglas de ruta: un rol equivocado seguiría dando 403); quitar la anotación de seguridad de los tests o condicionarla a un perfil (RF-40); construir el MockMvc a mano con `MockMvcBuilders...apply(springSecurity())` invertido (más frágil y repetido en 5 clases).

### 2.5 Verificación de los web origins sin ruta en Keycloak (RF-12)

**Decisión**: verificación en tres capas, todas en T7: (1) **lectura del JSON versionado**: `webOrigins` = `["http://localhost:5173","http://localhost:8081"]` sin ruta y `redirectUris` con `/*`; (2) **importación limpia**: `docker compose up` importa el realm sin errores en los logs de Keycloak; (3) **comprobación funcional**: `curl -i -X OPTIONS "http://localhost:8180/realms/gestion-pedidos/protocol/openid-connect/token" -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: POST"` devuelve `access-control-allow-origin: http://localhost:5173`; si en esta versión el preflight del endpoint de token no respondiera, se comprueba en la consola de administración que el cliente muestra los dos web origins sin ruta (mismo resultado, vía de respaldo). Además se decodifica el `iss` del token para confirmar el emisor fijo `http://localhost:8180/realms/gestion-pedidos` (RF-5).
**Descartada**: dar por bueno solo la lectura del JSON — la spec pide verificar que la versión elegida los **admite**, y la prueba funcional es la que lo demuestra.

## 3. Archivos y responsabilidades

| Fichero | Acción | Responsabilidad | RF |
|---|---|---|---|
| `Backend/pom.xml` | **editar** | Las tres dependencias de RF-46 (sin versiones; BOM de Boot 4.1.2). | RF-46 |
| `Backend/src/main/java/gestionpedidos/config/SecurityConfig.java` | **crear** | `@Configuration` + `@EnableMethodSecurity`; bean `SecurityFilterChain` (stateless, CSRF off, CORS integrado, entry point 401, access denied handler 403, matriz de rutas con `denyAll` por defecto, `oauth2ResourceServer` JWT); bean `corsConfigurationSource` con `app.cors.allowed-origin`. | RF-1, RF-17 a RF-25, RF-29, RF-30, RF-31, RF-47, RF-48 |
| `Backend/src/main/java/gestionpedidos/config/ConverterRolesJwt.java` | **crear** | **Punto único de conversión** (RF-9): claim `realm_access.roles` → autoridades `ROLE_TERMINAL/COCINA/RECOGIDA/ADMIN`. | RF-8, RF-9 |
| `Backend/src/main/java/gestionpedidos/config/EntryPointNoAutenticado.java` | **crear** | `AuthenticationEntryPoint`: 401 con cuerpo exacto de RF-2 y cabecera `WWW-Authenticate: Bearer`. | RF-2, RF-3, RF-32 |
| `Backend/src/main/java/gestionpedidos/config/ManejadorAccesoDenegado.java` | **crear** | `AccessDeniedHandler`: 403 con el cuerpo de RF-31 (denegación de ruta con token válido). | RF-31, RF-32 |
| `Backend/src/main/java/gestionpedidos/config/PoliticaTransicionEstado.java` | **crear** | Componente de política fail-closed de la comprobación por estado destino (RF-26); se invoca desde el `@PreAuthorize` del controlador. | RF-26 |
| `Backend/src/main/java/gestionpedidos/config/OpenApiConfig.java` | **crear** | Bean `OpenAPI` con esquema de seguridad Bearer JWT y requisito global, para el botón «Authorize» de Swagger. | RF-19 |
| `Backend/src/main/java/gestionpedidos/controller/PedidoController.java` | **editar** | `@PreAuthorize` sobre `cambiarEstadoDelPedido` con la política (`#dto.estado`); **sin cambios de firma ni de lógica**. | RF-26, RF-28 |
| `Backend/src/main/java/gestionpedidos/exception/GlobalExceptionHandler.java` | **editar** | Añadir `@ExceptionHandler(AccessDeniedException.class)` → 403 con el cuerpo de RF-27, por especificidad **antes** del catch-all. | RF-27, RF-33 |
| `Backend/src/main/resources/application.properties` | **editar** | `spring.security.oauth2.resourceserver.jwt.issuer-uri=${JWT_ISSUER_URI}` y `...jwk-set-uri=${JWT_JWK_SET_URI}` **sin valor por defecto**. | RF-4 |
| `Backend/src/test/resources/application-test.properties` | **editar** | Las dos propiedades JWT con **valores ficticios** de solo test (sin red, sin variables de entorno en el CI). | RF-37, RF-41 |
| `Backend/src/test/java/gestionpedidos/config/SeguridadTestConfig.java` | **crear** | `@TestConfiguration` de solo test: `JwtDecoder` simulado que devuelve un token con rol ADMIN para un valor fijo (y lanza `JwtException` con otro). | RF-37, RF-38 |
| `Backend/src/test/java/gestionpedidos/controller/*.java` (5 clases) | **editar** | `@AutoConfigureMockMvc(addFilters = false)` en cada una (solo la anotación; ninguna aserción cambia). | RF-36 |
| `Backend/src/test/java/gestionpedidos/PedidoIntegrationTest.java` | **editar** | `@Import(SeguridadTestConfig)` y cabecera `Authorization: Bearer …` con token ADMIN en todas las peticiones. | RF-38 |
| `Backend/src/test/java/gestionpedidos/seguridad/SeguridadRutasTest.java` | **crear** | Matriz endpoint × rol, cuerpos exactos 401/403, ruta desconocida, token inválido/sin rol, preflight OPTIONS, swagger público. | RF-17 a RF-25, RF-29 a RF-32, RF-39, RF-48 |
| `Backend/src/test/java/gestionpedidos/seguridad/SeguridadTransicionesTest.java` | **crear** | Transición por rol/destino, cuerpo nulo, precedencia 401→403→400→403→400/404, doble fallo y RF-34 (403 y no 500). | RF-26, RF-27, RF-34, RF-43 |
| `docker-compose.yml` | **editar** | Servicio `keycloak` (imagen fija, 8180 en 127.0.0.1, importación de realm, admin con `:?`) y variables `JWT_*` del `backend`. | RF-5, RF-6, RF-15, RF-44, RF-45 |
| `keycloak/realm-gestion-pedidos.json` | **crear** | Realm versionado: 4 roles, usuarios demo (contraseñas ficticias), cliente `frontend-app` público con PKCE S256/redirect URIs/web origins/300 s y cliente `dev-test` con direct access grant; sin secretos. | RF-7, RF-11, RF-12, RF-13, RF-14 |
| `.env.example` | **editar** | `KEYCLOAK_ADMIN_PASSWORD=` vacía + `KEYCLOAK_ADMIN_USERNAME`, `JWT_ISSUER_URI` y `JWT_JWK_SET_URI` documentadas por nombre, sin valores reales. | RF-44, RF-51 |
| `.github/workflows/ci.yml` | **editar** | Job `docker`: valor ficticio de `KEYCLOAK_ADMIN_PASSWORD` en «Validar Compose» y en el paso negativo sin `DB_PASSWORD`, más el paso gemelo sin `KEYCLOAK_ADMIN_PASSWORD`. | RF-41, RF-44 |
| `.github/dependabot.yml` | **editar** | Ignore `version-update:semver-major` de `keycloak/keycloak` en la entrada `docker-compose`, con comentario en español. | RF-50 |
| `AGENTS.md` | **editar** | Línea 11 («ruta inexistente → 404» → «401 sin token y 403 con token válido»; aclara 400 vs 409), sección Entorno (JWT por nombre), sección Docker (dos contraseñas obligatorias; puerto del frontend → editar el realm). | RF-17, RF-27, RF-44, RF-49, RF-51 |
| `README.md` | **editar** | Configuración (dos contraseñas, variables JWT, puerto 8180, ejemplo exacto de curl con token, «cambiar el puerto del frontend exige editar el JSON del realm») y nota de estado intermedio (frontend en 401 hasta la 009). | RF-14, RF-44, RF-49, RF-51 |
| `Backend/README.md` | **editar** | Tabla de errores (fila 404 sigue describiendo «recurso no encontrado» con **fila nueva** para ruta inexistente → 401/403; `Backend/README.md:263` pasa la transición de 409 a **400** y deja el 409 para duplicados de catálogo), JWT, puerto 8180 y curl. | RF-17, RF-27, RF-49, RF-51 |
| `Backend/postman/API de gestión de pedidos - Hamburguesería.postman_collection.json` | **editar** | Carpeta «Seguridad» con la petición de token (cliente `dev-test`) y nota del Bearer. | RF-14, RF-49 |
| `MEMORY.md` | **editar** (al final, ≤ ~50 líneas) | Dependencias aprobadas, versión fija de Keycloak + ignore de Dependabot, estado intermedio, aprendizajes. | RF-49, RF-50 |

**No se tocan**: `Frontend/**` (fuera de alcance hasta la 009), `Backend/src/main/java/gestionpedidos/model|repository|service/**`, `dto/**` (ningún DTO cambia), `docs/constitution.md` (lo actualizará la 009), los Dockerfile y `Cargo`/`package.json`.

## 4. Cambios por capa (backend) y frontend

- **model**: sin cambios (la máquina de estados y sus mensajes no se tocan; RF-27/RF-28).
- **repository**: sin cambios.
- **service**: **sin cambios** (RF-28: la validación de orden sigue solo en `PedidoService.cambiarEstadoDelPedido`, misma firma; `PedidoServiceTest` intacto, RF-35).
- **controller**: solo `PedidoController.cambiarEstadoDelPedido` gana `@PreAuthorize` con la política; el resto de controladores no cambia (la autorización de sus rutas vive en la cadena).
- **dto**: **sin cambios** (RF-26 evalúa el estado con `#dto.estado` sobre el DTO existente; añadirle `@NotNull` rompería el 400 actual — decisión 8.9).
- **exception**: `GlobalExceptionHandler` gana el manejador de `AccessDeniedException` (403 RF-27); los demás manejadores y mensajes existentes no cambian.
- **config**: es donde nacen todos los ficheros nuevos de seguridad (SecurityConfig, converter, entry point, access denied handler, política, OpenAPI).
- **Frontend (api, hooks, components, pages)**: **no aplica** — cero ficheros. Efecto aceptado en la spec: entre la 008 y la 009 el frontend actual recibirá 401 en todos sus endpoints (estado intermedio documentado en T9/T10).

## 5. Contrato de la API

### 5.1 Autorización por ruta (matriz completa; todo lo no listado, denegado por defecto)

| Método y ruta | Roles con acceso | RF |
|---|---|---|
| `OPTIONS /**` (preflight CORS) | públicas (sin token) | RF-18, RF-48 |
| `GET /doc/swagger-ui.html`, `GET /doc/swagger-ui/**`, `GET /v3/api-docs`, `GET /v3/api-docs/**` | públicas en desarrollo local | RF-19 |
| `GET /api/productos`, `GET /api/categorias`, `GET /api/categorias/{id}`, `GET /api/terminales`, `GET /api/terminales/{id}` | TERMINAL, COCINA, RECOGIDA, ADMIN | RF-20 |
| `POST /api/productos`, `PUT /api/productos/{id}`, `PATCH /api/productos/{id}/estado`, `POST /api/categorias`, `POST /api/terminales` | solo ADMIN | RF-21 |
| `POST /api/pedidos` | TERMINAL, ADMIN | RF-22 |
| `POST /api/pedidos/{pedidoId}/productos`, `DELETE /api/pedidos/{pedidoId}/eliminar-producto/{productoId}` | COCINA, ADMIN | RF-23 |
| `GET /api/pedidos` (con o sin `?estado=`) | COCINA, RECOGIDA, ADMIN | RF-24 |
| `GET /api/pedidos/codigo/{codigo}` | RECOGIDA, ADMIN | RF-25 |
| `PATCH /api/pedidos/{pedidoId}/estado` | ruta: COCINA, RECOGIDA, ADMIN; **además**, política por estado destino (§6.2): COCINA→PREPARACION/LISTO, RECOGIDA→PAGADO/ENTREGADO, ADMIN→cualquiera, TERMINAL→ninguno (queda fuera de la ruta) | RF-26 |
| `GET /api/estadisticas/productos-mas-vendidos` | TERMINAL, COCINA, RECOGIDA, ADMIN | RF-29 |
| `GET /api/estadisticas/ranking-terminales` | solo ADMIN | RF-30 |
| **Cualquier otra ruta** | **denegada**: sin token → `401`; con token válido → `403` (en lugar del 404 sin protección) | RF-17 |

### 5.2 JSON de éxito — sin cambios

Se mantiene tal cual lo que ya fija AGENTS.md: `POST`/`PUT` de entidad de primer nivel → `{"mensaje": "...", "data": {...}}` (p. ej. `POST /api/pedidos` → 201); listados `GET` → array directo; sub-recursos de pedido → DTO directo (p. ej. `PATCH .../estado` → 200 con el `PedidoDto`); `DELETE .../eliminar-producto` → DTO de la línea o `204`; `POST /api/productos|categorias|terminales` → 201 con sobre.

### 5.3 JSON de error nuevos (letra por letra, RF-2, RF-31, RF-27)

- **401 No autenticado** (token ausente, caducado, firma/emisor/formato incorrectos — el mismo cuerpo para los tres casos), cabecera `WWW-Authenticate: Bearer`, `Content-Type: application/json` (UTF-8):

```json
{"Error: ": "No autenticado: token ausente, caducado o inválido"}
```

- **403 No autorizado por ruta** (token válido pero el rol no permite la operación; también rutas desconocidas con token):

```json
{"Error: ": "No autorizado: el rol del usuario no permite esta operación"}
```

- **403 No autorizado por transición** (política de estado destino, RF-26/RF-27):

```json
{"Error: ": "No autorizado: el rol del usuario no permite esta transición de estado"}
```

- **Errores existentes sin cambios**: `400 {"Error: ": "Transición de estado no permitida: X → Y"}` (orden, desde el servicio), `400` JSON mal formado/`{"Error": ..., "detalles": [...]}` de `@Valid`, `404 {"Error: ": "..."}` de recurso no encontrado, `409 {"Error: ": "..."}` de `PedidoStateException` (duplicados de catálogo) y `500 {"Error inesperado: ": "..."}`.

## 6. Algoritmo en pseudocódigo

### 6.1 Cadena de filtros (precedencia exacta de RF-43)

```text
PETICIÓN
│
├─[1] Filtro CORS de seguridad (bean corsConfigurationSource, origen = app.cors.allowed-origin)
│     SI es preflight OPTIONS → respuesta 200/204 con cabeceras CORS, SIN token → FIN   (RF-18, RF-48)
│
├─[2] Autenticación Bearer (JWT contra JWT_ISSUER_URI / JWT_JWK_SET_URI)
│     SI no hay Authorization        → continúa anónimo
│     SI el token es inválido/caducado/firma o emisor malos
│                                    → EntryPoint → 401 {"Error: ": "No autenticado: ..."} + WWW-Authenticate: Bearer  (RF-1, RF-2, RF-3)
│     SI es válido                   → SecurityContext con ROLE_* (converter único: claim realm_access.roles → ROLE_*)  (RF-8, RF-9)
│
├─[3] Autorización de RUTA (matriz de §5.1)
│     SI es pública (OPTIONS / swagger) → pasa
│     SI es anónimo y la ruta no es pública → AccessDenied → como es anónimo → EntryPoint → 401 (RF-2)
│     SI está autenticado y el rol no cubre la ruta  → AccessDeniedHandler → 403 RF-31           (RF-31)
│     SI la ruta no existe ni está listada (denyAll) → anónimo: 401 · autenticado: 403 RF-31      (RF-17)
│     (aquí termina la precedencia 401 → 403 de ruta: TERMINAL con JSON mal formado en el PATCH
│      recibe 403 RF-31, porque la ruta se evalúa ANTES de llegar al cuerpo)
│
├─[4] DISPATCHER SERVLET
│     [4a] Resolución de argumentos (PARSEo, antes que cualquier método):
│          JSON mal formado o estado inexistente → HttpMessageNotReadableException
│                                    → 400 {"Error: ": "El cuerpo de la petición no es válido: ..."}   (RF-43 precisión)
│          (para COCINA/RECOGIDA/ADMIN: este 400 ocurre ANTES de la política → sin 403 por el contenido del cuerpo)
│     [4b] Seguridad a nivel de MÉTODO (@PreAuthorize → PolíticaTransicionEstado, fail-closed):
│          destino = #dto.estado
│          SI autenticación nula o sin rol relevante → false (fail-closed)
│          SI ROLE_ADMIN   → true  (cualquier destino, incluido nulo → luego el servicio responde 400)
│          SI destino NULL → false (COCINA/RECOGIDA no pueden decidir → 403 RF-27)
│          SI ROLE_COCINA  → true ssi destino ∈ {PREPARACION, LISTO}
│          SI ROLE_RECOGIDA→ true ssi destino ∈ {PAGADO, ENTREGADO}
│          SI no → false (TERMINAL o rol desconocido)
│          false → AccessDeniedException → @ExceptionHandler en GlobalExceptionHandler
│                                    → 403 {"Error: ": "No autorizado: ... transición de estado"} (RF-27, RF-33; jamás 500)
│     [4c] Controlador → PedidoService (SIN CAMBIOS):
│          pedido inexistente → 404 · orden violado (X → Y) → 400 "Transición de estado no permitida: X → Y"
│          duplicados de catálogo → 409 · resto de errores de negocio → tal como hoy
```

Resultado de la precedencia, idéntico al de RF-43: **401 → 403 de ruta → 400 de parseo → 403 de política → 400/404 del servicio**.

### 6.2 Política de transición (componente)

```text
funcion permitido(destino: EstadoPedido, auth: Authentication) -> boolean:   # fail-closed
    si auth es nula o no autenticado → false
    si auth.authorities contiene ROLE_ADMIN → true
    si destino es nulo → false
    si ROLE_COCINA   → destino en {PREPARACION, LISTO}
    si ROLE_RECOGIDA → destino en {PAGADO, ENTREGADO}
    si no → false                        # TERMINAL (aunque llegara), rol desconocido o sin rol
```

## 7. Interfaz

No hay interfaz de usuario nueva (el frontend no se toca; el login es la 009). La «interfaz» de esta spec es:

- **Swagger UI** (`/doc/swagger-ui.html`) accesible sin token en local, con esquema **Bearer JWT** declarado y botón «Authorize» para probar el catálogo con un token de ADMIN (RF-19) — bean `OpenAPI` en `OpenApiConfig`.
- **Contrato HTTP** de §5 (lo que consumirá la spec 009 y cualquier cliente).
- **Verificación con curl** documentada en README raíz y Backend/README: obtener token con el cliente de pruebas y llamar a la API (RF-14, RF-16), incluida la cabecera `WWW-Authenticate: Bearer` del 401.
- **Colección Postman** con la petición de token para reproducirlo desde la GUI.

## 8. Decisiones justificadas (con la alternativa descartada)

**8.1 Autorización de ruta por `SecurityFilterChain` con `anyRequest().denyAll()` (RF-17).**
Denegado por defecto: una ruta desconocida con token da 403 (y sin token, 401) en lugar del 404 que daría sin protección.
*Descartada*: `anyRequest().authenticated()` — una ruta desconocida con token válido pasaría la autorización y llegaría al dispatcher → 404, incumpliendo RF-17.

**8.2 Entrypoint y AccessDeniedHandler propios, no los de Spring Security.**
Escriben **exactamente** los cuerpos de RF-2 y RF-31 con `Content-Type: application/json` (RF-32).
*Descartados*: los handlers por defecto (devuelven `WWW-Authenticate` con `error=...` y cuerpos distintos, o problem-details) y manejarlos desde el `@ControllerAdvice` (la cadena de seguridad responde **antes** de llegar al `@ControllerAdvice`).

**8.3 Dos destinos distintos para `AccessDeniedException`.**
El `AccessDeniedHandler` de la cadena → 403 de **ruta** (RF-31); el `@ExceptionHandler(AccessDeniedException.class)` del `GlobalExceptionHandler` → 403 de **política** (RF-27). No se confunden: las denegaciones de ruta terminan en la cadena (el `DispatcherServlet` nunca se entera) y las de política se lanzan dentro del dispatcher, donde el manejador de excepciones las resuelve **antes** de que propaguen a la cadena (por eso el handler del advice gana y jamás hay 500, RF-33/RF-34).
*Descartado*: un único cuerpo para ambos (RF-27 y RF-31 exigen mensajes distintos).

**8.4 Seguridad a nivel de método SOLO en el PATCH de estado, con componente de política (RF-26, RF-28).**
La ruta no puede decidir el destino (depende del cuerpo) y la comprobación debe correr «por delante del servicio, antes de invocarlo».
*Descartadas*: comprobar el rol dentro de `PedidoService` (cambiaría su firma y obligaría a tocar `PedidoServiceTest`, RF-28/RF-35); una `HandlerInterceptor` que lea el cuerpo (no puede leer un `@RequestBody` ya consumible sin duplicar el parseo); anotar también el resto de métodos (sus reglas ya las cubre la ruta).

**8.5 El cuerpo sin estado se evalúa en la política, no con `@NotNull` en el DTO.**
ADMIN pasa la política → servicio → 400 como hoy; COCINA/RECOGIDA → 403 RF-27 (fail-closed); TERMINAL → 403 RF-31 por la ruta, con independencia del cuerpo (RF-26, RF-27).
*Descartada*: `@NotNull` en `EstadoPedidoRequestDto` — dispararía un 400 de validación **antes** de la política para todos los roles (incluidos COCINA/RECOGIDA, que deben recibir 403) y además cambiaría un DTO que ya consume el frontend (⚠️ Pregunta antes).

**8.6 `@PreAuthorize("@politicaTransicionEstado.permite(#dto.estado, authentication)")` con el nombre del parámetro.**
Spring Boot 4.1 compila con `-parameters` (heredado del parent), de modo que `#dto` resuelve; si por algún motivo no resolviera, la alternativa válida es `#p0.estado`.
*Descartado*: expresiones con `#p0` directamente (menos legible en un proyecto didáctico).

**8.7 Variables JWT sin defecto en `application.properties`, con `:-` locales en Compose (RF-4, RF-15).**
`application.properties` lleva `${JWT_ISSUER_URI}` y `${JWT_JWK_SET_URI}` sin valor por defecto (RF-4: en local ni en producción); el Compose les da por defecto las URLs locales (`http://localhost:8180/realms/gestion-pedidos` y `http://keycloak:8180/.../certs`), sobrescribibles desde `.env`, como hace con `DB_USERNAME`.
*Descartado*: `${JWT_ISSUER_URI:?...}` en Compose — haría obligatorias las dos variables en **cualquier** comando y rompería los pasos del CI de RF-41/RF-44, que solo pasan `DB_PASSWORD` y `KEYCLOAK_ADMIN_PASSWORD`.
*Nota*: el **issuer** es fijo (`localhost:8180`) y las **claves** cambian entre IDE (`localhost`) y Docker (`keycloak`), que es exactamente RF-5/RF-6.

**8.8 `KC_HOSTNAME=http://localhost:8180` para el emisor fijo (RF-5).**
Con la opción de hostname v2 de Keycloak 26, el `iss` de cualquier token es `http://localhost:8180/realms/gestion-pedidos` **independientemente** del host por el que se acceda (IDE, `localhost` desde el host o `keycloak` desde la red interna), y el backend solo necesita alcanzar las claves por la URL interna.
*Descartada*: dejar el hostname dinámico (modo dev) — un token obtenido vía `keycloak:8180` llevaría `iss=http://keycloak:8180/...` y el backend, que valida contra `localhost:8180`, lo rechazaría.

**8.9 La contraseña del administrador del proveedor se llama `KEYCLOAK_ADMIN_PASSWORD` en el `.env` y se mapea a `KC_BOOTSTRAP_ADMIN_PASSWORD` (RF-44).**
El nombre que fija la spec es el de la **variable del proyecto** (la que exige `:?` en Compose y la que documentan README/AGENTS/.env.example); `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` de Keycloak eran las opciones antiguas y Keycloak 26 usa las de bootstrap.
*Descartado*: usar la variable antigua de Keycloak dentro del servicio (no es la que exige la spec y está obsoleta en 26.x).

**8.10 Decoder simulado y propiedades ficticias SOLO en `src/test` (RF-37, RF-40).**
`application-test.properties` (que está en `src/test`) recibe URLs ficticias para que ningún contexto de test pida variables de entorno ni red; `PedidoIntegrationTest` añade un `JwtDecoder` simulado que solo acepta el token de prueba con rol ADMIN. En `src/main` no hay ningún mecanismo que permita apagar la seguridad.
*Descartado*: un perfil `test` o propiedad en `src/main` que desactive la seguridad (RF-40 la prohíbe explícitamente).

**8.11 Los tests nuevos de seguridad son `@WebMvcTest` con `@Import(SecurityConfig…)` y `jwt()`, no `@SpringBootTest`.**
Prueban autorización sin contenedor de base de datos: rápidos, sin red y sin Testcontainers; los servicios van mockeados con `@MockitoBean` porque solo se verifica la capa de seguridad.
*Descartado*: `@SpringBootTest` para todo (multiplicaría el tiempo de la suite y no aporta: la BD no interviene en la autorización). La integración de pedidos **sigue** siendo `@SpringBootTest` con token ADMIN (RF-38), que es la única que necesita el servidor real.

**8.12 Tres clases de test nuevo de seguridad (rutas y transiciones separadas).**
`SeguridadRutasTest` (matriz por ruta, cuerpos 401/403, desconocidas, preflight, swagger) y `SeguridadTransicionesTest` (destino, nulos, precedencia, doble fallo, RF-34) mantienen cada responsabilidad localizable; sin ellas, una falla no diría qué regla se rompió.
*Descartado*: un único test gigante (ilegible) o una clase por RF (infla la suite).

**8.13 Versión fija `keycloak/keycloak:26.8.2` y `start-dev --import-realm` (RF-45, RF-7).**
Etiqueta de parche exacta (reproducible y apta para el ignore de mayores de Dependabot) y modo desarrollo con importación automática del JSON versionado.
*Descartadas*: `latest`/`26.8` (no fijan versión); importar el realm a mano con `kc.sh import` (paso manual, olvidadizo); `start` en modo producción (exige TLS y certificados).

## 9. Estrategia de tests

### 9.1 Tests existentes — qué cambia y qué no (RF-35 a RF-38)

| Grupo | Nº | Cambio permitido | RF |
|---|---|---|---|
| Controlador (`@WebMvcTest`) | 54 | **Solo** añadir `@AutoConfigureMockMvc(addFilters = false)`; ninguna aserción cambia; siguen **sin token** y sin cargar la política. | RF-36 |
| Servicio (Mockito) | 55 | **Ninguno** — `PedidoService` ni su firma se tocan. | RF-28, RF-35 |
| Repositorio (`@DataJpaTest`) | 6 | Ninguno. | RF-35 |
| Contexto / integración / migración | 5 | Propiedades JWT ficticias en `application-test.properties`; `PedidoIntegrationTest` además envía token ADMIN con decoder simulado (único cambio de comportamiento). | RF-37, RF-38 |
| `MotorPostgresqlValidatorTest` | 4 | Ninguno. | RF-35 |

Comprobación transversal: `git diff` vacío en `model/`, `repository/`, `service/`, en `dto/` y en `PedidoServiceTest`.

### 9.2 Tests nuevos (RF-39)

- **`SeguridadRutasTest`** — `@WebMvcTest(PedidoController|ProductoController|…)` + `@Import(SecurityConfig, PoliticaTransicionEstado)` + `@MockitoBean JwtDecoder` + `jwt()`/`jwt().authorities(...)` de `spring-security-test`:
  - por cada fila de la matriz de §5.1: **200** con rol permitido, **403** con rol no permitido, **401** sin token;
  - cuerpos **letra por letra**: 401 = RF-2 (+ `WWW-Authenticate: Bearer` y `Content-Type: application/json`), 403 de ruta = RF-31;
  - ruta inexistente: 401 sin token / 403 con token (RF-17);
  - token sin rol alguno o con rol desconocido → solo públicas (RF-18);
  - token inválido (decoder que lanza `JwtException`) → 401 con el cuerpo de RF-2 (RF-2);
  - preflight `OPTIONS /api/pedidos` con `Origin` → 200/204 sin token (RF-48);
  - `/v3/api-docs` sin token no devuelve 401/403 en el slice (llega al dispatcher → la regla es permitAll; el 200 real se verifica con curl en T10, RF-19).
- **`SeguridadTransicionesTest`** — mismo molde, centrado en `PATCH /api/pedidos/{id}/estado`:
  - matriz destino × rol (incluidos los 403 de RF-27 con su cuerpo exacto y el 403 de RF-31 para TERMINAL);
  - **doble fallo**: COCINA `CREADO→PAGADO` → **403 RF-27**, nunca 400 (RF-43);
  - rol que cubre el destino pero salta el orden (p. ej. COCINA `CREADO→LISTO`, ADMIN `CREADO→ENTREGADO`) → 400 «Transición de estado no permitida: X → Y»;
  - cuerpo **sin estado o nulo**: ADMIN → 400 (servicio), COCINA/RECOGIDA → 403 RF-27 (fail-closed), TERMINAL → 403 RF-31;
  - **precedencia**: TERMINAL + JSON mal formado → 403 RF-31 (ruta antes del parseo); COCINA/RECOGIDA/ADMIN + JSON mal formado o estado inexistente → **400** de parseo **antes** de cualquier 403 de política;
  - sin token → 401 antes que nada;
  - **RF-34**: con `@MockitoBean PoliticaTransicionEstado` que deniega → **403** con el cuerpo de RF-27 y **no 500**.
- **Sin repositorios con `@Query` nuevos** → no hay tests de repositorio que añadir (RF-35).

### 9.3 CI (RF-41, RF-42)

- `mvn -B -f Backend/pom.xml test` sigue **sin variables de entorno**: los contextos usan propiedades ficticias/decoder simulado; ningún test contacta con Keycloak (que **no existe** en el CI).
- Job `docker`: los tres pasos de Compose con valores ficticios (T8) no levantan nada ni necesitan red.
- Verde = 124 existentes + los nuevos (RF-42).

### 9.4 Verificación manual (con el usuario al final)

1. `mvn -f Backend/pom.xml test` en verde con el recuento completo.
2. `docker compose up` (con `.env` de ejemplo): Keycloak importa el realm; curl → 200 con el rol adecuado, 401 sin token (cuerpo RF-2 + `WWW-Authenticate`), 403 con rol insuficiente (RF-31) y 403 de transición (RF-27) (RF-16).
3. Preflight de Keycloak (web origins sin ruta) e `iss` del token (§2.5).
4. Swagger sin token con el botón «Authorize» (RF-19).
5. `docker compose config` con y sin cada contraseña (RF-44) — en el CI, los tres pasos gemelos.
6. Tras fusionar y con el usuario: «Check for updates» de Dependabot sin errores ni PR de salto mayor de Keycloak (RF-50).
7. Frontend: **sin cambios** → no requiere build/lint ni verificación en navegador; se deja constancia del estado intermedio (401 hasta la 009).

## 10. Riesgos y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| Un `@WebMvcTest` evalúa las propiedades `JWT_*` sin resolver (placeholder) al tener el resource-server en el classpath | T1 lo comprueba en cuanto añade las dependencias; si ocurre, se añaden las dos propiedades ficticias con `@TestPropertySource` **en las 5 clases de test** (nunca en `src/main`). |
| La política sí se carga en algún slice y el `@PreAuthorize` falla con «No bean named …» | Comprobación explícita en T1/T3: si pasa, revisar que `@EnableMethodSecurity` y la política siguen solo en `SecurityConfig`/`@Component` y no se importan en los tests (decisión 2.1). |
| La anotación de filtros cambió de paquete en Boot 4 | Ya verificado en el Javadoc 4.1.2: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` + `addFilters`. |
| `#dto` no resuelve en el `@PreAuthorize` | Compilación con `-parameters` heredada del parent; alternativa `#p0.estado` (decisión 8.6). |
| El JWKS del decoder ficticio provoca llamadas de red en los tests | Los contextos de test usan URLs ficticias y el `JwtDecoder` simulado sustituye al auto-configurado (`@ConditionalOnMissingBean`); ningún test decodifica contra la red (RF-41). |
| Keycloak ignora los web origins sin ruta o rechaza los redirect con `/*` | Verificación funcional del preflight en T7 (§2.5); si fallara, se revisa el JSON contra la consola de la versión 26.8.2 antes de dar T7 por hecha. |
| El `docker compose config` del CI falla por la nueva variable obligatoria | T8 pasa `KEYCLOAK_ADMIN_PASSWORD=ci-dummy` en los pasos previstos por RF-41/RF-44; el nuevo paso gemelo es el que la comprueba ausente. |
| Estado intermedio: el frontend actual recibe 401 | Aceptado y documentado en la spec (despliegue posterior a la 009); se anota en README/MEMORY en T9/T10. |

## 11. Cobertura de RF (RF-1 a RF-51)

- **RF-1, RF-2, RF-3** → cadena JWT + `EntryPointNoAutenticado` (T1) → tests de 401 y cabecera (T4).
- **RF-4** → propiedades sin defecto en `application.properties` + `.env.example`/docs (T1, T6, T9).
- **RF-5, RF-6** → servicio Keycloak con `KC_HOSTNAME` fijo y URLs internas/locales en Compose (T6) + `iss` verificado (T7).
- **RF-7** → `keycloak/realm-gestion-pedidos.json` con roles, usuarios demo y sin secretos (T7).
- **RF-8, RF-9** → `ConverterRolesJwt` como único punto de conversión (T2) + tests (T4).
- **RF-10** → ADMIN cubierto en toda la matriz (T2) + tests 200 de ADMIN (T4).
- **RF-11, RF-12, RF-13** → cliente `frontend-app` en el JSON (T7) + verificación (T7) + documentación del puerto del frontend → realm (T9).
- **RF-14** → cliente `dev-test` con direct access grant (T7) + curl y Postman (T7, T9, T10).
- **RF-15** → Compose con todo configurado por variables ya previstas (T6, T7).
- **RF-16** → curl 200/401/403 con cuerpos exactos (T7, T10).
- **RF-17** → `denyAll` (T2) + tests 401/403 de ruta desconocida (T4) + fila nueva de documentación (T9).
- **RF-18** → OPTIONS públicas y nota de Actuator fuera de alcance (T2) + test de preflight (T4).
- **RF-19** → `OpenApiConfig` (Bearer JWT) + swagger permitAll (T2) + test de no-401 en slice (T4) + verificación real con curl (T10).
- **RF-20 a RF-25, RF-29, RF-30** → matriz en `SecurityConfig` (T2) + tests endpoint × rol (T4).
- **RF-26** → `PoliticaTransicionEstado` + `@PreAuthorize` (T3) + tests de destino/nulos (T5).
- **RF-27** → cuerpo de la política + `@ExceptionHandler(AccessDeniedException)` (T3) + tests (T5) + corrección de la fila 409→400 en Backend/README (T9).
- **RF-28** → servicio intacto: sin cambios de firma ni en `PedidoServiceTest` (T3, verificado en T5/T10).
- **RF-31** → `ManejadorAccesoDenegado` (T1) + tests (T4).
- **RF-32** → entry point/handler/advice escriben los cuerpos exactos (T1, T3) + aserciones letra por letra (T4, T5).
- **RF-33** → `@ExceptionHandler(AccessDeniedException)` antes del catch-all (T3).
- **RF-34** → test con política simulada que deniega → 403 y no 500 (T5).
- **RF-35** → sin cambios en servicio/repositorio/validador (T3, verificado en T5 y T10).
- **RF-36** → `@AutoConfigureMockMvc(addFilters = false)` en los 5 slices y política no cargada (T1, decisión 2.1).
- **RF-37** → propiedades ficticias + decoder simulado solo en test (T1).
- **RF-38** → token ADMIN en `PedidoIntegrationTest` (T1).
- **RF-39** → `SeguridadRutasTest` y `SeguridadTransicionesTest` (T4, T5).
- **RF-40** → ningún fichero de `src/main` apaga la seguridad (revisión en T10).
- **RF-41** → suite sin red + valores ficticios en el CI (T1, T8) + proveedor ausente del CI (T8).
- **RF-42** → `mvn test` verde con 124 + nuevos (T10).
- **RF-43** → precedencia codificada por diseño (§6.1) y testeada en el orden exacto (T5).
- **RF-44** → `:?` de `KEYCLOAK_ADMIN_PASSWORD` en Compose (T6) + dos pasos negativos gemelos del CI (T8) + documentación (T9).
- **RF-45** → imagen `26.8.2` fija, 8180 solo en 127.0.0.1, realm importado y ausencia en el CI (T6, T7, T8).
- **RF-46** → exactamente tres dependencias, **con aprobación del usuario** (T1).
- **RF-47** → stateless + CSRF desactivado (T1).
- **RF-48** → `corsConfigurationSource` integrado en la cadena (T1) + test de preflight (T4).
- **RF-49** → README raíz, AGENTS.md, Backend/README, colección Postman y MEMORY (T9, T10), con la corrección del 409 limitada a `Backend/README.md:263` y la línea 11 de AGENTS.md.
- **RF-50** → ignore de mayores de `keycloak/keycloak` con comentario en español (T8) + MEMORY y «Check for updates» tras fusionar (T10).
- **RF-51** → `JWT_ISSUER_URI`/`JWT_JWK_SET_URI` por nombre, sin valores reales, en AGENTS.md, README raíz, Backend/README y `.env.example` (T6, T9).
