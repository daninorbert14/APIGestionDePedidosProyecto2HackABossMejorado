# Spec 008 — Seguridad: backend como resource server JWT con roles TERMINAL, COCINA, RECOGIDA y ADMIN
Estado: aprobada

## Contexto y objetivo
Hoy el backend no autentica nada: cualquier persona que llegue a la API puede crear pedidos, cambiar estados, editar el catálogo o ver estadísticas. El objetivo de esta spec es proteger todos los endpoints del backend con tokens JWT emitidos por un proveedor de identidad, asignando permisos según cuatro roles de negocio (TERMINAL, COCINA, RECOGIDA y ADMIN), devolviendo siempre los errores 401 y 403 con el mismo formato de error que ya usa el resto de la API, y manteniendo la suite de pruebas ejecutable en el CI sin levantar el proveedor.

El alcance es el backend y el servicio del proveedor de identidad en el entorno de desarrollo local (Docker). El login del frontend queda fuera: será la spec 009, pero esta spec deja fijado el contrato que la 009 necesita para funcionar sin tocar después el backend.

**Estado intermedio aceptado y explícito**: entre la 008 y la 009 el frontend actual recibirá 401 en todos sus endpoints (pasan a estar protegidos). No hay impacto público porque el despliegue (paso 8 del plan de proyecto) es posterior a la 009 y las dos PRs se fusionan seguidas.

**Decisiones cerradas con el usuario** (respondidas a las 5 preguntas de la fase de clarificación): alcance de la 008, matriz de permisos, roles y proveedor, formato de errores y estrategia con los tests existentes; se recogen como requisitos abajo.

## Usuarios
- Usuario de terminal de autoservicio (futuro, con la 009): crea pedidos desde la carta; no consulta la lista de pedidos.
- Usuario de cocina: consulta los pedidos y avanza su preparación; añade o quita productos en el estado permitido.
- Usuario de recogida: busca pedidos por código y cobra/entrega.
- Administrador: gestiona el catálogo (por Swagger, a propósito, no en el frontend) y tiene acceso a todo, incluidas las estadísticas de ranking.
- Desarrollador del proyecto: verifica la seguridad localmente con curl y un token, y ejecuta las pruebas en el CI sin proveedor.
- Persona que evalúa el proyecto: ve una API protegida con autorización por rol y una suite de pruebas que la cubre.

## Historias de usuario
- HU-1. Como administrador del sistema, quiero que ningún endpoint acepte peticiones sin token válido, para que nadie acceda a la API por accidente o por omisión.
- HU-2. Como usuario con un rol concreto, quiero que solo pueda hacer las operaciones de mi rol, para que un terminal no manipule el catálogo ni un cocinero cobre pedidos.
- HU-3. Como cliente de la API (frontend en la 009), quiero recibir errores 401 y 403 en el mismo formato JSON que el resto de la API, para tratarlos de forma uniforme.
- HU-4. Como desarrollador, quiero verificar la seguridad localmente con curl usando el cliente de pruebas del realm (direct access grant, exclusivamente local), para comprobar la protección de la API sin depender del frontend.
- HU-5. Como desarrollador, quiero que las pruebas automáticas simulen los tokens sin levantar el proveedor en el CI, para que la suite siga siendo rápida y no dependa de red.
- HU-6. Como autor del repositorio, quiero que ningún secreto real esté versionado, para que el repositorio sea seguro de hacer público.

## Definiciones
- **Token válido**: token JWT cuya emisión procede del proveedor configurado (emisor correcto), cuya firma es válida y que no está caducado.
- **Rol de realm / autoridad**: el rol llega en el token en el bloque de roles de realm del proveedor; el sistema lo expone internamente como autoridad con el prefijo ROLE_ (ROLE_TERMINAL, ROLE_COCINA, ROLE_RECOGIDA, ROLE_ADMIN). Hablando de permisos, «rol» y «autoridad ROLE_…» son lo mismo.
- **Transición de estado**: cambio de un pedido entre estados de la máquina CREADO → PREPARACION → LISTO → PAGADO → ENTREGADO, sin saltos; su validación de orden ya existe y no se toca.
- **Estado destino**: el estado al que apunta la transición pedida en el PATCH de estado; el rol se decide únicamente por este valor (RF-26), mientras que el orden se valida solo en el servicio (RF-28).
- **Preflight CORS**: la petición OPTIONS que el navegador hace antes de una petición real; debe seguir funcionando sin token para no romper el CORS existente.
- **Cliente público / con PKCE**: contrato de login de la 009: flujo Authorization Code con PKCE y cliente sin secreto, de modo que el navegador no lleve secretos.
- **Realm importado**: configuración del proveedor de identidad versionada como JSON, con usuarios de demostración y sin secretos reales.

## Requisitos funcionales

### Validación de tokens
- RF-1: CUANDO llegue una petición a un endpoint protegido, EL SISTEMA validará el token contra el proveedor (emisor y claves de firma) y lo rechazará si no es válido.
- RF-2: SI la petición no lleva token, o el token está caducado, o es inválido (emisor, firma o formato incorrectos), ENTONCES EL SISTEMA responderá 401 con el cuerpo `{"Error: ": "No autenticado: token ausente, caducado o inválido"}`, el mismo mensaje para los tres casos, para no dar pistas al atacante.
- RF-3: El 401 llevará la cabecera estándar `WWW-Authenticate: Bearer`, el Content-Type `application/json` y codificación UTF-8, igual que el resto de respuestas de error de la API.
- RF-4: La dirección de emisión de tokens (issuer) y la dirección de descarga de claves serán las variables de entorno `JWT_ISSUER_URI` y `JWT_JWK_SET_URI`, **sin valor por defecto en `application.properties`** (ni en local ni en producción): los valores locales se fijarán fuera del repositorio, en la Run Config del IDE y en el `.env` del Compose, del mismo modo que `DB_*`; en producción serán obligatorias.
- RF-5: EL SISTEMA usará como emisor de desarrollo local una instancia de Keycloak publicada en un puerto distinto al del backend (8180 frente al 8080) para que los dos servicios puedan convivir, con el nombre de emisor fijo e independiente del puerto por el que se acceda, de modo que el token siga validándose igual desde el IDE que desde Docker.
- RF-6: La dirección de claves será interna cuando el backend y el proveedor corran en Docker, y la dirección local cuando el backend corre en el IDE; cambiar de una a otra no exige tocar código.
- RF-7: El realm de desarrollo se importará desde un JSON versionado en el repositorio, con los cuatro roles de realm y usuarios de demostración cuyas contraseñas son ficticias y quedan documentadas como de solo demostración, y sin ningún secreto real (la contraseña de administración del proveedor incluida: ficticia o fuera del repositorio).

### Roles
- RF-8: EL SISTEMA leerá los roles del token del bloque de roles de realm (formato por defecto del proveedor) y no de roles de cliente, porque existe una sola API y un solo frontend.
- RF-9: EL SISTEMA convertirá los cuatro roles de realm en las autoridades ROLE_TERMINAL, ROLE_COCINA, ROLE_RECOGIDA y ROLE_ADMIN en un único punto de conversión aislado; SI en el futuro cambia el proveedor o el formato del claim, ENTONCES solo se tocará ese punto, sin recorrer el resto del código de seguridad.
- RF-10: ADMIN tendrá acceso a todos los endpoints.

### Contrato para la spec 009 (login del frontend)
- RF-11: El proveedor tendrá un cliente para el frontend configurado para flujo Authorization Code con PKCE, cliente público sin secreto.
- RF-12: Ese cliente incluirá entre sus **redirect URIs** los patrones fijos `http://localhost:5173/*` y `http://localhost:8081/*` (con comodín de ruta) y entre sus **web origins** los orígenes **sin ruta** `http://localhost:5173` y `http://localhost:8081`, declarados en el JSON estático del realm (no derivados de la variable FRONTEND_URL), de modo que el CORS ya existente del backend siga valiendo sin cambios; cambiar el puerto del frontend exigirá editar el JSON del realm, y así se documentará **en el README raíz (sección Configuración) y en AGENTS.md**, que son las fuentes donde quedará verificable.
- RF-13: EL SISTEMA ofrecerá desde ya el contrato completo que la spec 009 necesita —cliente público con PKCE S256 (RF-11), redirect URIs y web origins fijos (RF-12), `accessTokenLifespan` de 300 s y roles de realm convertidos en autoridades ROLE_* (RF-8, RF-9)—, todo declarado en el JSON versionado del realm, de modo que la 009 no tenga que tocar el backend para funcionar.

### Entorno de desarrollo y verificación con curl
- RF-14: EL SISTEMA dispondrá en el realm de desarrollo local de un cliente de pruebas con el flujo de acceso directo (direct access grant) activado, exclusivamente para verificación local; su documentación indicará claramente que es solo para uso local y no debe existir en producción.
- RF-15: CUANDO el backend arranque junto al proveedor en el entorno local (IDE o Docker), EL SISTEMA funcionará sin configuración manual adicional a la de las variables de entorno ya previstas (JWT_ISSUER_URI, JWT_JWK_SET_URI y las del administrador del proveedor, cuyos valores reales no se versionan en el .env.example).
- RF-16: EL SISTEMA permitirá verificar la seguridad con curl: obtener un token con el cliente de pruebas y llamar a un endpoint protegido, obteniendo 200 con el rol adecuado y 401/403 sin él.

### Matriz de permisos (todo lo no listado, denegado por defecto)
- RF-17: EL SISTEMA denegará por defecto cualquier endpoint no listado en esta matriz, con o sin token: una **ruta inexistente** responderá 401 sin token y 403 con token válido (denegado por defecto), en lugar del 404 que devolvería sin protección.
- RF-18: Públicas (sin token): únicamente las peticiones OPTIONS de preflight CORS. La documentación OpenAPI en local se rige por RF-19 y Actuator ya no figura en esta lista: su regla pública la fijará la spec de despliegue (ver «Fuera de alcance»).
- RF-19: La documentación OpenAPI (`/doc/swagger-ui.html` y `/v3/api-docs`) será **pública en el entorno de desarrollo local**: no expone datos, solo el contrato de la API. Su configuración declarará un esquema de seguridad Bearer JWT para que Swagger UI ofrezca el botón «Authorize» y el ADMIN pueda probar el catálogo con su token.
- RF-20: Catálogo lectura — GET `/api/productos`, GET `/api/categorias`, GET `/api/categorias/{id}`, GET `/api/terminales`, GET `/api/terminales/{id}`: accesibles con cualquiera de los roles TERMINAL, COCINA, RECOGIDA o ADMIN.
- RF-21: Catálogo escritura — POST `/api/productos`, PUT `/api/productos/{id}`, PATCH `/api/productos/{id}/estado`, POST `/api/categorias`, POST `/api/terminales`: solo ADMIN (se gestiona por Swagger a propósito).
- RF-22: POST `/api/pedidos`: accesible con TERMINAL y ADMIN.
- RF-23: POST `/api/pedidos/{pedidoId}/productos` y DELETE `/api/pedidos/{pedidoId}/eliminar-producto/{productoId}`: accesibles con COCINA y ADMIN. La regla de negocio actual (solo en estado CREADO; si se quita la última línea se borra el pedido y responde 204) la sigue aplicando el servicio y no se modifica.
- RF-24: GET `/api/pedidos` (con o sin `?estado=`): accesible con COCINA, RECOGIDA y ADMIN.
- RF-25: GET `/api/pedidos/codigo/{codigo}`: accesible con RECOGIDA y ADMIN. TERMINAL no consulta pedidos: recibe el suyo en la respuesta de su propio POST.
- RF-26: PATCH `/api/pedidos/{pedidoId}/estado` se autoriza **solo por el estado destino de la transición pedida**, con esta matriz: COCINA cuando el estado destino sea PREPARACION o LISTO; RECOGIDA cuando el estado destino sea PAGADO o ENTREGADO; ADMIN para cualquier estado destino; TERMINAL para ninguno. La ruta admite COCINA, RECOGIDA y ADMIN (TERMINAL queda fuera de la ruta) y la comprobación por estado destino se hace **por delante del servicio**, antes de invocarlo, mediante **seguridad a nivel de método en el controlador** apoyada en un **componente de política**; la comprobación es **fail-closed**: sin autenticación (o cuando el componente no puede decidir) no se concede el paso. CUANDO el cuerpo de la petición **no incluya el estado destino o sea nulo**, EL SISTEMA evaluará la política igualmente: ADMIN pasa la política y el servicio responde 400 como hoy; COCINA y RECOGIDA se deniegan con el 403 de RF-27 (no pueden decidir el destino, fail-closed); TERMINAL, que está fuera de la ruta, recibe el 403 de RF-31 con independencia del cuerpo.
- RF-27: CUANDO la transición pedida no corresponda al rol del usuario según su estado destino (RF-26), ENTONCES EL SISTEMA responderá 403 con el cuerpo `{"Error: ": "No autorizado: el rol del usuario no permite esta transición de estado"}`; CUANDO la transición corresponda al rol del usuario pero viole el orden de la máquina de estados, ENTONCES se responderá 400 (BadRequestException) con el cuerpo `{"Error: ": "Transición de estado no permitida: X → Y"}` (X = estado actual, Y = estado destino), como hasta ahora, sin cambios: el rol no exime del orden. CUANDO el cuerpo no lleve estado (o sea nulo), ENTONCES COCINA y RECOGIDA reciben este mismo 403 de transición (RF-26) y ADMIN, que pasa la política, llega al servicio, que responde 400 como hoy; TERMINAL, que está fuera de la ruta, recibe el 403 de RF-31 con independencia del cuerpo.
- RF-28: La comprobación por rol de la transición (RF-26) se hará **solo por el estado destino y por delante del servicio**, residiendo en la seguridad a nivel de método del controlador con su componente de política (fail-closed sin autenticación, RF-26); la validación de orden de la máquina de estados seguirá estando **solo en el servicio**, sin duplicarla en la capa de protección de rutas ni en otra parte. El servicio **no cambiará de firma** y sus tests (PedidoServiceTest, RF-35) no se tocan: es decisión de esta spec separar rol (por delante) y orden (servicio) para que ninguno de los dos se evalúe en dos sitios que puedan divergir.
- RF-29: GET `/api/estadisticas/productos-mas-vendidos`: accesible con cualquiera de los roles TERMINAL, COCINA, RECOGIDA o ADMIN (alimenta «Los más vendidos» de la carta).
- RF-30: GET `/api/estadisticas/ranking-terminales`: solo ADMIN.

### Errores de autorización
- RF-31: SI el token es válido pero el rol no permite la operación (cualquier 403 que no sea el de transición; p. ej. TERMINAL en el PATCH de estado, que queda fuera de la ruta), ENTONCES EL SISTEMA responderá 403 con el cuerpo `{"Error: ": "No autorizado: el rol del usuario no permite esta operación"}`.
- RF-32: Los 401 y 403 emitidos por la propia cadena de protección (aunque no pasen por el manejador global de excepciones) escribirán exactamente los cuerpos de RF-2, RF-31 y RF-27, con Content-Type `application/json` y UTF-8.
- RF-33: SI una comprobación de permisos hecha dentro del proceso (controlador o servicio) lanza la excepción de acceso denegado, ENTONCES EL SISTEMA la traducirá al 403 correspondiente (RF-31 o RF-27) en un manejador específico situado antes del catch-all; jamás se convertirá en un 500 «Error inesperado».
- RF-34: Existe un test que demuestra RF-33: con una política simulada que deniega, la comprobación de permisos responde 403 con el cuerpo correcto y no 500.
- RF-43: CUANDO una misma petición pueda fallar en varios puntos de control, EL SISTEMA evaluará los errores en este orden: primero 401 (RF-2), después 403 —permiso de la ruta o rol por estado destino (RF-26, RF-27, RF-31)— y solo al final los 404 y 400 que devuelva el servicio; así nunca se responderá un 404 o un 400 del servicio cuando un 401 o un 403 ya explicaba el fallo. **Precisión**: los 400 que se generan al **parsear o validar la petición** (JSON mal formado, valor de estado inexistente) se producen **antes de evaluar la política** (la política está por delante del servicio, no por delante del parseo), por lo que se responden sin llegar a la comprobación de rol, incluso para un rol que la política habría denegado; en esos casos no se emite el 403 de RF-27 ni el de RF-31 por el contenido del cuerpo.

### Pruebas y CI
- RF-35: Las pruebas sin HTTP no se modificarán: servicio (55), repositorio (6) y `MotorPostgresqlValidatorTest` (4, también sin HTTP), que son los tests existentes que esta spec **no toca**; sumadas a los 54 de controlador (RF-36) y a los 5 de contexto/integración/migración (RF-37) cuadran los 124 tests existentes, con este desglose: **54 controlador + 55 servicio + 6 repositorio + 5 contexto/integración/migración + 4 validador = 124**.
- RF-36: Los 54 tests de controlador existentes se mantendrán sin token; cada clase desactivará los filtros de seguridad en el propio test (mediante la anotación equivalente a desactivar filtros en Spring Boot 4.1), porque prueban contrato HTTP y lógica de negocio, no autorización. El componente de política de la comprobación por estado destino (RF-26) **no se cargará en esos slices**; si se cargara, esos tests usarían un bean de política permisivo, sin perfiles, y ese bean permisivo existirá **únicamente en fuentes de prueba (src/test), nunca en src/main**, coherente con RF-40.
- RF-37: Los 5 tests de contexto e integración existentes (1 de contexto de la aplicación, 2 de integración de Pedido y 2 de migración de Flyway) arrancarán sin red, usando un decodificador de token simulado o propiedades ficticias; la suite no necesita variables de entorno nuevas.
- RF-38: El test de integración de pedidos (peticiones reales con TestRestTemplate) será el único test existente cuyo **comportamiento** cambie: enviará un token de pruebas con rol ADMIN. Los demás tests existentes sí cambiarán de anotaciones o propiedades (RF-36, RF-37), pero no cambian lo que verifican.
- RF-39: EL SISTEMA tendrá tests nuevos de seguridad, aparte de los existentes, que verifiquen: la matriz endpoint × rol (200/401/403 según esta spec), los cuerpos exactos de 401 y 403 letra por letra, la regla de transiciones del PATCH por rol (incluido el cuerpo sin estado o nulo) y la precedencia de errores (RF-43) en este orden exacto: 401 → 403 de ruta → 400 de parseo/servicio → 403 de política → 400/404 del servicio; en concreto, el doble fallo de los casos límite, que TERMINAL con JSON mal formado en el PATCH de estado recibe el 403 de ruta (autorización de ruta antes del parseo) y que COCINA, RECOGIDA o ADMIN con JSON mal formado o estado inexistente recibe el 400 de parseo/servicio anterior a la política, sin 403 por el contenido del cuerpo.
- RF-40: Prohibido desactivar la seguridad en el código de producción mediante un perfil o propiedad de configuración (un application-test que la apague o una configuración ligada al perfil test deja la posibilidad de activarla por error en un despliegue): la seguridad real está siempre en el código y son los tests los que la saltan uno a uno.
- RF-41: El CI ejecutará la suite sin contactar con ningún proveedor de identidad: los tests **nuevos de seguridad (RF-39)** usan tokens simulados (jwt() de spring-security-test), mientras que los 54 tests de controlador existentes siguen **sin token** (RF-36), y los de contexto usan decodificador simulado o propiedades ficticias; el único valor relacionado con el proveedor que maneja el CI es uno ficticio de `KEYCLOAK_ADMIN_PASSWORD`, que se le pasa al paso «Validar Compose» para que la interpolación no falle y también al paso negativo «el Compose debe fallar sin contraseña» para que falle únicamente por la ausencia de `DB_PASSWORD`, al que acompaña un paso gemelo que comprueba que el Compose falla también sin `KEYCLOAK_ADMIN_PASSWORD`; ningún paso contacta con el proveedor (el proveedor no existe en el CI, RF-45).
- RF-42: `mvn test` quedará en verde con los 124 tests existentes más los nuevos de seguridad.

### Keycloak en docker-compose (entorno local)
- RF-44: SI a un comando de Compose le falta la contraseña del administrador del proveedor (`KEYCLOAK_ADMIN_PASSWORD`), ENTONCES el propio comando fallará antes de levantar nada, con la misma obligatoriedad que `DB_PASSWORD`; su valor real no se versiona (`.env.example` la deja vacía) y el README raíz y AGENTS.md documentarán que **cualquier comando de Compose exige las dos contraseñas** (DB_PASSWORD y KEYCLOAK_ADMIN_PASSWORD), comprobado en el CI con dos pasos negativos gemelos: el Compose falla sin `DB_PASSWORD` (con un valor ficticio de `KEYCLOAK_ADMIN_PASSWORD`, para que falle solo por esa ausencia, RF-41) y el Compose falla sin `KEYCLOAK_ADMIN_PASSWORD`.
- RF-45: EL proveedor de identidad local en Compose usará una imagen con etiqueta de versión fija, publicará su puerto 8180 solo en 127.0.0.1 (no en todas las interfaces) e importará el realm desde el JSON versionado en el repositorio; el proveedor no existirá en el CI: los tests siguen sin red (RF-41).

### Protección de la API: dependencias, estado y CORS
- RF-46: La protección se implementará con exactamente tres dependencias nuevas, nombradas de antemano: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` y `spring-security-test`; su incorporación queda sujeta a la aprobación explícita del usuario, y SI se necesitara cualquier otra dependencia nueva, ENTONCES se parará y se pedirá aprobación antes de usarla.
- RF-47: EL SISTEMA tratará la API como sin estado (stateless): no creará ni usará sesión de navegador y tendrá CSRF desactivado, porque la autenticación llega en cada petición como token Bearer y no en cookies de sesión; con CSRF activo, las peticiones con token pero sin token CSRF serían rechazadas.
- RF-48: El CORS existente quedará integrado en la cadena de seguridad de modo que las peticiones OPTIONS de preflight sigan resolviéndose sin token, sin romper el CORS actual (RF-12, RF-18).

### Documentación de la 008
- RF-49: La documentación quedará actualizada en el README raíz, AGENTS.md, MEMORY.md, Backend/README y la colección Postman, incluyendo el puerto del proveedor (8180), el ejemplo exacto de cómo obtener un token con curl para llamar a la API, y que **cambiar el puerto del frontend exige editar el JSON del realm**, documentado en el README raíz (sección Configuración) y en AGENTS.md (RF-12). En la tabla de errores de Backend/README la fila «404: recurso no encontrado» **sigue describiendo el 404 de recurso no encontrado**; «ruta inexistente» **sale de esa fila** y pasa a una **fila nueva** que describe 401 sin token y 403 con token válido (RF-17). La corrección del 409 es **solo** sobre dos fuentes concretas: `Backend/README.md:263` (la fila de la tabla de errores que hoy atribuye 409 a la transición de estado no permitida pasa a **400**, RF-27; el 409 de PedidoStateException corresponde a los **nombres duplicados de catálogo**) y **AGENTS.md línea 11**, donde «ruta inexistente → 404» pasa a «401 sin token y 403 con token válido» (RF-17); la aclaración sobre el mapeo 400 vs. 409 en AGENTS.md **no** tiene frase errónea que cambiar: solo aclara y consolida el mapeo 400 (transición de estado no permitida) vs. 409 (duplicados de catálogo), de modo que la documentación quede alineada con el comportamiento real del código.

### Dependabot (imagen de Keycloak)
- RF-50: En `.github/dependabot.yml`, la entrada `docker-compose` añadirá un ignore `version-update:semver-major` para la imagen de Keycloak, con un comentario en español en el propio archivo que explique su motivo y cuándo retirarlo (siguiendo la línea de RF-9 de la spec 007); las versiones menores y de parche seguirán permitidas y la imagen llevará etiqueta de versión fija (RF-45). La decisión quedará recogida en MEMORY.md al cerrar la spec y, tras fusionar, se verificará con «Check for updates» que no hay errores ni PR de salto mayor para Keycloak.

### Documentación de entorno (JWT)
- RF-51: Las variables `JWT_ISSUER_URI` y `JWT_JWK_SET_URI` quedarán documentadas **por nombre, sin valores reales**, en AGENTS.md (sección Entorno), el README raíz, Backend/README y `.env.example`, junto al resto de variables de entorno que ya se documentan en esas fuentes (RF-4).

## Requisitos no funcionales
- Los mensajes de error de seguridad están escritos en español, como el resto del proyecto.
- Ningún secreto real versionado: ni en el realm importado, ni en el .env.example (KEYCLOAK_ADMIN_PASSWORD vacía), ni en el código (las credenciales del administrador del proveedor solo como variables sin valor; RF-4, RF-44).
- La verificación manual con curl no requiere frontend: se hace con el cliente de pruebas del realm local.
- La suite de pruebas no gana dependencia de red ni de tiempos de arranque del proveedor (el CI no se ralentiza ni se vuelve inestable).
- El CORS existente del backend sigue funcionando tras la 008 (preflight OPTIONS público e integrado en la cadena de seguridad: RF-18, RF-48).

## Casos límite
- Petición sin token → 401 con el mensaje único de RF-2.
- Ruta inexistente (p. ej. `/api/inexistente`): sin token → 401 (RF-2); con token válido → 403 (RF-17, denegado por defecto), en lugar del 404 que devolvería sin protección.
- Token caducado → 401, mismo cuerpo que sin token (RF-2, no se distingue).
- Token con firma o emisor incorrectos → 401, mismo cuerpo (RF-2).
- Token válido sin rol alguno de los cuatro → solo accede a las públicas (RF-18); el resto, 403.
- Token con rol desconocido (no es ninguno de los cuatro) → tratado como «sin rol relevante»: 403 en lo protegido.
- Preflight CORS sin token → 200/204 público (RF-18, RF-48); si el navegador lo bloquease, el CORS existente se rompería.
- Swagger local sin token → accesible (público, RF-19): es documentación, no datos; con el esquema Bearer JWT declarado, «Authorize» permite al ADMIN probar el catálogo con su token.
- Cuando exista Actuator, `/actuator/health` sin token ya no es público (RF-18): responde 401 (RF-2) y con token que no lo cubra, 403 (RF-17); su regla pública la fijará la spec de despliegue (ver «Fuera de alcance»).
- TERMINAL pide GET `/api/pedidos` con token válido → 403 (RF-24, RF-25: no consulta pedidos).
- TERMINAL pide PATCH `/api/pedidos/{id}/estado` con token válido → 403 con el mensaje de RF-31 («no permite esta operación»): queda fuera de la ruta (RF-26), no es el 403 de transición.
- COCINA pide LISTO→PAGADO (estado destino fuera de su rol) → 403 con el mensaje de RF-27.
- **Doble fallo**: COCINA pide CREADO→PAGADO (el destino no es suyo *y* la transición salta el orden) → 403 con el mensaje de RF-27, **no 400** (RF-43): la comprobación por estado destino está delante del servicio; el 400 solo aparece cuando el rol sí cubre el destino.
- Un rol autorizado para el estado destino pide una transición que salta el orden (p. ej. COCINA con CREADO→LISTO o ADMIN con CREADO→ENTREGADO) → 400 con el mensaje «Transición de estado no permitida: X → Y» como hasta ahora (RF-27, RF-43), porque el rol no exime de la máquina de estados.
- ADMIN pide una transición inválida en el orden → 400 con ese mismo mensaje: el rol de ADMIN no salta la máquina de estados (RF-26: «cualquier estado destino»).
- PATCH de estado con **cuerpo sin estado o nulo**: ADMIN pasa la política y el servicio responde 400 como hoy; COCINA o RECOGIDA → 403 con el cuerpo de RF-27 (fail-closed, no pueden decidir el destino); TERMINAL → 403 con el cuerpo de RF-31 (fuera de la ruta, con independencia del cuerpo) (RF-26, RF-27).
- PATCH de estado con **JSON mal formado o valor de estado inexistente**: para **TERMINAL** (rol fuera de la ruta) → 403 con el cuerpo de RF-31, porque la autorización de ruta se evalúa **antes** que el parseo del cuerpo; para **COCINA, RECOGIDA o ADMIN** → el 400 de parseo/servicio de hoy, que se produce **antes de evaluar la política** (la política está por delante del servicio, no por delante del parseo): se responde sin llegar a la comprobación de rol, incluso para un rol que la política habría denegado; no se emite el 403 de RF-27 por el contenido del cuerpo (RF-43).
- Quitar la última línea de un pedido con COCINA → 204 y borrado, sin cambios (RF-23).
- Llamada del frontend actual a la API entre la 008 y la 009 → 401 (estado intermedio aceptado; sin impacto público porque el despliegue es posterior a la 009).
- El CI pasa valores ficticios de `KEYCLOAK_ADMIN_PASSWORD`: al paso «Validar Compose» (para que la interpolación no falle) y al paso negativo sin `DB_PASSWORD` (para que falle solo por esa ausencia), con su paso gemelo que comprueba el fallo sin `KEYCLOAK_ADMIN_PASSWORD`; los tests no tienen red hacia el proveedor ni variables nuevas → la suite pasa igual (RF-41, RF-44).
- Comando de Compose sin `KEYCLOAK_ADMIN_PASSWORD` (o sin `DB_PASSWORD`) → el propio comando falla antes de levantar nada (RF-44); en `.env.example` esa contraseña va vacía.
- Credenciales del administrador del proveedor sin definir → no se versionan valores reales (RF-7, RNF); el realm de demo usa contraseñas ficticias documentadas como de solo demostración (RF-7).
- Ejecutar el backend en el IDE y en Docker a la vez → compiten por el puerto 8080 (ya conocido, límite existente de Docker); el proveedor usa el 8180, publicado solo en 127.0.0.1, para no añadir nuevos choques (RF-5, RF-45).

## Fuera de alcance
- Pantalla de login, rutas protegidas por rol en el frontend, gestión del token en el navegador y su renovación: todo eso es la spec 009.
- Decidir el proveedor de identidad de producción: lo decide la spec de despliegue; por eso issuer y claves son variables sin valor por defecto (RF-4).
- La regla pública de Actuator (`/actuator/health`): en esta spec Actuator no es público (RF-18) y la regla pública que le corresponda la fijará la spec de despliegue.
- Desactivar la documentación OpenAPI en un perfil de producción: ese perfil lo traerá la spec de despliegue (junto con la regla de Actuator), así que la 008 no puede implementarlo ni comprobarlo; en la 008 el requisito de RF-19 se limita a que Swagger UI y `/v3/api-docs` sean públicos en local.
- Levantar el proveedor de identidad en el CI: los tests siguen sin red ni proveedor (RF-41, RF-45).
- Modificar la máquina de estados, las reglas de negocio de pedidos (CREADO, 204 al borrar la última línea) o los mensajes de error existentes distintos de 401/403.
- Gestión de catálogo en el frontend (sigue siendo backend/Swagger a propósito).
- Actualizar `docs/constitution.md` (principio 3, frase «Frontend … sin autenticación») y la frase de AGENTS.md línea 2 («frontend simple, sin estado global ni autenticación»): quedarán desactualizadas con la spec 009 (login del frontend). Las actualizará la 009; la 008 **no las toca**.
- Cambiar el formato de respuesta de los endpoints existentes (éxito o error) más allá de añadir los cuerpos 401/403.
- Autenticación de usuarios en el frontend, registro de usuarios o recuperación de contraseñas.

## Criterios de finalización
- `mvn -f Backend/pom.xml test` en verde con los 124 tests existentes más los nuevos de seguridad (RF-42), y el CI en verde: los tests sin red ni proveedor, el paso «Validar Compose» y el paso negativo sin `DB_PASSWORD` recibiendo un valor ficticio de `KEYCLOAK_ADMIN_PASSWORD`, más el paso gemelo que comprueba que el Compose falla sin `KEYCLOAK_ADMIN_PASSWORD` (RF-41, RF-44).
- Con curl y un token del cliente de pruebas local (RF-14, RF-16): 200 en un endpoint del rol, 401 sin token y 403 con rol insuficiente, con los cuerpos exactos de RF-2, RF-31 y RF-27 y cabecera WWW-Authenticate en el 401.
- La matriz de la sección «Matriz de permisos» está cubierta por tests endpoint × rol (RF-39) y coincide con la spec, incluida la precedencia **401 → 403 de ruta → 400 de parseo/servicio (antes de la política) → 403 de política → 400/404 del servicio** (TERMINAL con JSON mal formado en el PATCH de estado recibe el 403 de ruta; COCINA, RECOGIDA o ADMIN con JSON mal formado o estado inexistente reciben el 400 de parseo/servicio antes del 403 de política, RF-43), el cuerpo sin estado por rol (RF-26, RF-27) y el doble fallo COCINA CREADO→PAGADO → 403 (RF-43).
- Swagger local accesible sin token, con esquema Bearer JWT y «Authorize» para probar el catálogo con token de ADMIN (RF-19).
- Existe test que demuestra que un permiso denegado dentro del proceso responde 403 y no 500 (RF-34).
- Los tests existentes siguen verificando lo mismo de siempre: los sin HTTP intactos —servicio y repositorio (RF-35, PedidoServiceTest sin tocar: RF-28) y `MotorPostgresqlValidatorTest` (4 tests, RF-35)—, los de contexto/integración solo cambian anotaciones o propiedades (RF-37) y los de controlador sin token pero desactivando los filtros en el propio test (RF-36); el único cuyo comportamiento cambia es el test de integración de pedidos (RF-38).
- Ningún archivo de producción permite apagar la seguridad con un perfil o propiedad (RF-40), y la API queda stateless con CSRF desactivado (RF-47) con el CORS integrado en la cadena de seguridad (RF-48).
- Cualquier comando de Compose exige DB_PASSWORD y KEYCLOAK_ADMIN_PASSWORD (RF-44), con esta última vacía en el `.env.example`, la regla documentada en README raíz y AGENTS.md, y los dos pasos negativos gemelos del CI (falta de DB_PASSWORD con valor ficticio de KEYCLOAK_ADMIN_PASSWORD; falta de KEYCLOAK_ADMIN_PASSWORD); la imagen del proveedor lleva etiqueta fija, el 8180 solo se publica en 127.0.0.1 y el realm se importa del JSON versionado (RF-45).
- Las dependencias nuevas son exactamente las tres nombradas en RF-46 y su incorporación está aprobada por el usuario.
- La documentación (README raíz, AGENTS.md, MEMORY.md, Backend/README y colección Postman) incluye el puerto 8180 y cómo obtener un token con curl (RF-49); la corrección del 409 queda verificable sobre dos fuentes concretas: `Backend/README.md:263` describe la transición no permitida como 400 y el 409 (PedidoStateException) como duplicado de catálogo, en Backend/README la fila «404: recurso no encontrado» sigue describiendo el 404 de recurso no encontrado y «ruta inexistente» pasa a una fila nueva que describe 401 sin token y 403 con token válido (RF-17), AGENTS.md línea 11 cambia «ruta inexistente → 404» por «401 sin token y 403 con token válido» (RF-17) y AGENTS.md solo aclara además el mapeo 400 vs. 409 (ahí no hay frase errónea que cambiar).
- El README raíz (sección Configuración) y AGENTS.md documentan que cambiar el puerto del frontend exige editar el JSON del realm (RF-12, RF-49).
- Las variables `JWT_ISSUER_URI` y `JWT_JWK_SET_URI` aparecen documentadas por nombre, sin valores reales, en AGENTS.md (sección Entorno), el README raíz, Backend/README y `.env.example` (RF-51).
- `.github/dependabot.yml` incluye el ignore `version-update:semver-major` de la imagen de Keycloak con su comentario en español (RF-50); tras fusionar, «Check for updates» no muestra errores ni un PR de salto mayor de Keycloak, y la decisión queda recogida en MEMORY.md.
- El realm versionado existe, con cuatro roles y usuarios de demo con contraseñas ficticias documentadas como de solo demostración, y no contiene secretos reales (RF-7); el .env.example solo expone nombres de variables sin valores reales (RF-15, RF-44).
- El contrato de la 009 (RF-11, RF-12, RF-13) es verificable **leyendo el JSON versionado del realm**: cliente público, PKCE S256, redirect URIs con comodín `/*` (`http://localhost:5173/*`, `http://localhost:8081/*`), web origins **sin ruta** (`http://localhost:5173`, `http://localhost:8081`) y `accessTokenLifespan` de 300 s, suficiente para que la spec 009 no toque el backend.
- El estado intermedio (frontend en 401 hasta la 009) está documentado y aceptado.

## Dudas abiertas
- [PARA EL PLAN] Verificar en el plan que el componente de política de la comprobación por estado destino (RF-26) **no se carga en los slices de los 54 tests**; si se cargara, esos tests usarían un bean de política permisivo, sin perfiles, y ese bean permisivo **solo podrá existir en fuentes de prueba (src/test), nunca en src/main** (RF-36, RF-40).
- [PARA EL PLAN] Versión exacta de Keycloak (etiqueta fija de la imagen) y, con ella, verificar que la versión elegida **admite web origins sin ruta** (`http://localhost:5173`, `http://localhost:8081`) junto a redirect URIs con comodín `/*` (RF-12).
- [PARA EL PLAN] Compatibilidad de la API de Spring Security 7 con Spring Boot 4.1.
- [PARA EL PLAN] Nombre/anotación exacta para desactivar filtros en Spring Boot 4.1 (@AutoConfigureMockMvc(addFilters = false) o equivalente).

Estas cuatro dudas son para la fase de plan: se resuelven al redactar plan.md y **no bloquean la aprobación de la spec** (por eso usan el formato [PARA EL PLAN] y no [NECESITA ACLARACIÓN]).
