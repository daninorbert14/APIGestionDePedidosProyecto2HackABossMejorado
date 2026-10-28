# Gestión de Pedidos API

API REST desarrollada con **Spring Boot** para la gestión de pedidos en un entorno de terminales (tipo restaurante / cocina).  
Permite gestionar terminales, categorías, productos y pedidos con sus líneas de productos y control de estados.

---

## Tecnologías utilizadas

- Java 17+
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- PostgreSQL
- Lombok
- Swagger / SpringDoc OpenAPI

---

## Arquitectura del proyecto

El proyecto sigue una arquitectura en capas:

Controller -> Service -> Repository -> Database

Además se utilizan:

- DTOs para la comunicación entre capas y evitar exponer entidades directamente
- Entidades JPA para el modelo de datos
- Mapeos manuales Model ↔ DTO
- Manejo global de excepciones con `@RestControllerAdvice`
- Validación de entrada con Bean Validation (`@Valid`, `@NotBlank`, `@DecimalMin`...)

---

## Entidades principales

- **Terminal** → Punto de atención donde se generan pedidos
- **Categoría** → Clasificación de productos
- **Producto** → Artículos disponibles para vender
- **Pedido** → Ticket de compra generado desde una terminal
- **PedidoProducto** → Línea de pedido (relación Pedido ↔ Producto con cantidad y precio unitario)
- **EstadoPedido** → Enum que controla el flujo del pedido

---

## Estados del pedido

Los pedidos siguen este flujo:

CREADO -> PREPARACION -> LISTO -> PAGADO -> ENTREGADO

No se permiten saltos de estado.

| Estado | Descripción |
|---|---|
| `CREADO` | El pedido acaba de generarse en la terminal |
| `PREPARACION` | La cocina ha comenzado a prepararlo |
| `LISTO` | El pedido está listo para recoger |
| `PAGADO` | El cliente ha abonado el importe |
| `ENTREGADO` | El pedido ha sido entregado al cliente |

---

##  Funcionalidades principales

### Terminales
- Crear terminal
- Listar terminales
- Obtener terminal por ID

### Categorías
- Crear categoría (con validación de duplicados)
- Listar categorías
- Obtener categoría por ID

### Productos
- Crear producto (con validación de duplicados y campos obligatorios)
- Listar productos con filtros combinables: activo, categoría, ordenación y tipo de orden
- Actualizar producto
- Activar / desactivar producto

### Pedidos
- Crear pedido desde una terminal con productos iniciales
- Añadir productos a un pedido existente (acumula cantidad si ya existe la línea)
- Modificar cantidades de un pedido en estado CREADO (añadir o quitar unidades) en Cocina
- Eliminar productos de forma total o parcial vía `?cantidad=N`
- El pedido se elimina automáticamente cuando su última línea es removida en Cocina
- Cambiar estado del pedido respetando el flujo definido
- Obtener pedido por código único generado automáticamente
- Listar pedidos con filtro opcional por estado, ordenados por fecha ascendente

### Estadísticas
- Ranking de productos más vendidos (cantidad total agregada)
- Ranking de terminales por número de pedidos gestionados

---

## Endpoints principales

### Terminales

POST /api/terminales
GET /api/terminales
GET /api/terminales/{id}

### Categorías

POST /api/categorias
GET /api/categorias
GET /api/categorias/{id}

### Productos

POST   /api/productos
GET    /api/productos?activo=true&categoriaId=1&orden=precio&tipoOrden=DESC
PUT    /api/productos/{id}
PATCH  /api/productos/{id}/estado?activo=true

### Pedidos

POST   /api/pedidos
POST   /api/pedidos/{pedidoId}/productos
DELETE /api/pedidos/{pedidoId}/eliminar-producto/{productoId}?cantidad=2
PATCH  /api/pedidos/{pedidoId}/estado
GET    /api/pedidos/codigo/{codigo}
GET    /api/pedidos?estado=LISTO

### Estadísticas

GET /api/estadisticas/productos-mas-vendidos
GET /api/estadisticas/ranking-terminales

---

## Ejemplos de uso

### Crear un producto

**Request** `POST /api/productos`
```json
{
  "nombre": "Hamburguesa Clásica",
  "precio": 8.50,
  "activo": true,
  "categoriaId": 1
}
```

**Response** `201 Created`
```json
{
  "mensaje": "Producto creado correctamente",
  "data": {
    "id": 3,
    "nombre": "Hamburguesa Clásica",
    "precio": 8.50,
    "activo": true,
    "nombreCategoria": "Principales"
  }
}
```

---

### Crear un pedido

**Request** `POST /api/pedidos`
```json
{
  "terminalId": 1,
  "productosComprados": {
    "3": 2,
    "5": 1
  }
}
```

**Response** `201 Created`
```json
{
  "mensaje": "Pedido registrado correctamente",
  "data": {
    "id": 12,
    "codigo": "PED-A3F9C1B2",
    "fechaCreacion": "2025-06-10T14:32:00",
    "estado": "CREADO",
    "total": 24.75,
    "terminalId": 1,
    "productos": [
      {
        "productoId": 3,
        "nombreProducto": "Hamburguesa Clásica",
        "cantidad": 2,
        "precioUnitario": 8.50,
        "subtotal": 17.00
      },
      {
        "productoId": 5,
        "nombreProducto": "Refresco",
        "cantidad": 1,
        "precioUnitario": 2.75,
        "subtotal": 2.75
      }
    ]
  }
}
```

---

### Cambiar estado de un pedido

**Request** `PATCH /api/pedidos/12/estado`
```json
{
  "estado": "PREPARACION"
}
```

**Response** `200 OK`
```json
{
  "id": 12,
  "codigo": "PED-A3F9C1B2",
  "estado": "PREPARACION",
  "...": "..."
}
```

---

### Respuesta de error de validación

**Request** `POST /api/productos` con nombre vacío
```json
{
  "nombre": "",
  "precio": -5.00,
  "activo": true,
  "categoriaId": 1
}
```

**Response** `400 Bad Request`
```json
{
  "Error": "Datos de entrada inválidos",
  "detalles": [
    "nombre: El nombre no puede estar vacío",
    "precio: El precio debe ser mayor que 0"
  ]
}
```

> **Nota:** la clave de los errores de validación es `Error` (con mayúscula). El resto de errores de la API usan claves distintas según el código de estado:
>
> | Código | Cuerpo | Casos |
> |---|---|---|
> | `400` | `{"Error: ": "..."}` | validación de negocio, parámetro obligatorio ausente, tipo de parámetro inválido, JSON mal formado |
> | `404` | `{"Error: ": "..."}` | recurso no encontrado, ruta inexistente |
> | `409` | `{"Error: ": "..."}` | transición de estado no permitida |
> | `500` | `{"Error inesperado: ": "..."}` | error no controlado |

---



## Configuración del proyecto

En `application.properties`:

```properties
spring.application.name=Gestion_de_pedidos_API

spring.datasource.url=${DB_URL}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true

# Habilitar api-docs y swagger-ui
springdoc.api-docs.enable=true
springdoc.swagger-ui.enable=true
springdoc.swagger-ui.path=/doc/swagger-ui.html

# Origen permitido para CORS (el frontend en desarrollo)
app.cors.allowed-origin=${FRONTEND_URL:http://localhost:5173}
```

Configura las variables de entorno antes de ejecutar (Spring Boot no lee archivos `.env`; definelas en la configuración de tu IDE o en la terminal):

DB_URL=jdbc:postgresql://localhost:5432/gestion_pedidos

DB_USERNAME=postgres

DB_PASSWORD=

`FRONTEND_URL` es opcional: si no se define, el backend acepta peticiones desde `http://localhost:5173` (el puerto por defecto de Vite en desarrollo). En un despliegue real, se define esta variable apuntando al dominio del frontend en producción, y el backend dejará de aceptar peticiones desde `localhost`.

> **Importante:** no subas credenciales reales al repositorio.

---

## Documentación API (Swagger)

Una vez ejecutado el proyecto:
http://localhost:8080/doc/swagger-ui.html

### Colección Postman

En la carpeta postman/ de este repositorio encontrarás el archivo:
API de gestión de pedidos - Hamburguesería.postman_collection.json.

Para importarla:
1. Abre Postman
2. Clic en **Import**
3. Selecciona o arrastra el archivo `.json`
4. Todos los endpoints estarán listos para probar

---

## Cómo ejecutar el proyecto

```bash
# 1. Clonar el repositorio
git clone <url-del-repo>

# 2. Configurar las variables de entorno (ver sección de configuración)

# 3. Ejecutar
mvn spring-boot:run

# 4. Probar endpoints con Postman o Swagger
```

> **Alternativa con Docker**: desde la raíz del repositorio puedes levantar todo el sistema (base de datos + backend + frontend) con `docker compose up --build`, o solo la base de datos con `docker compose up --wait db` y arrancar el backend desde el IDE. Ver el README de la raíz. Recuerda que el backend del IDE y el de Docker no pueden compartir el puerto 8080 a la vez.

---

## Tests

El proyecto incluye una suite de tests en varias capas, escritos con **JUnit 5**, **Mockito** y **Spring Test**, que cubre desde la lógica de negocio aislada hasta el flujo completo de una petición HTTP real contra una base de datos. La suite completa son **124 tests** (54 de controller, 55 de service, 6 de repositorio, 2 de integración, 1 de contexto de aplicación, 4 del validador de configuración y 2 de migraciones).

### Tests unitarios de `Service` (Mockito)

Cubren la lógica de negocio de forma aislada, sin base de datos ni contexto de Spring — los repositorios se simulan con `@Mock` y se inyectan con `@InjectMocks`.

| Clase de test | Qué cubre |
|---|---|
| `ProductoServiceTest` | Creación, actualización, listado (filtrado por estado/categoría y ordenación por precio/nombre), y activación/desactivación de productos |
| `PedidoServiceTest` | Registro de pedidos, gestión de líneas de producto (añadir sumando cantidad o creando línea nueva, eliminar total o parcialmente), consulta por código, y la máquina de estados de transición (`CREADO → PREPARACION → LISTO → PAGADO → ENTREGADO`) |
| `CategoriaServiceTest` | Listado, creación (con validación de duplicados) y consulta de categorías |
| `TerminalServiceTest` | Listado, creación (con validación de duplicados) y consulta de terminales |
| `EstadisticasServiceTest` | Delegación correcta hacia los repositorios de productos más vendidos y ranking de terminales |

Cada método público está cubierto con su caso de éxito y sus casos de error correspondientes (recursos no encontrados, nombres duplicados, transiciones de estado no permitidas, validaciones de negocio), siguiendo el patrón **Arrange / Act / Assert**.

### Tests de la capa web (`@WebMvcTest` + `MockMvc`)

Verifican que cada controller expone correctamente sus endpoints, con el `Service` mockeado: rutas, verbos HTTP, deserialización del body, y que `GlobalExceptionHandler` traduce cada excepción de negocio al código HTTP correcto (`404` para recursos no encontrados, `400` para validaciones, `409` para conflictos de duplicados).

- `ProductoControllerTest`, `PedidoControllerTest`, `CategoriaControllerTest`, `TerminalControllerTest`, `EstadisticasControllerTest`

### Tests de repositorio (`@DataJpaTest`)

Las dos consultas de agregación (`@Query` con `SUM`/`COUNT` y `GROUP BY`) se prueban contra PostgreSQL mediante Testcontainers, no contra un mock — la única forma de verificar que el SQL generado hace lo que debe.

- `ProductoRepositoryTest` — ranking de productos más vendidos
- `PedidoRepositoryTest` — ranking de terminales por uso

### Test de integración de extremo a extremo (`TestRestTemplate`)

`PedidoIntegrationTest` levanta la aplicación completa en un puerto aleatorio y ejecuta el flujo real de crear categoría → producto → terminal → pedido vía peticiones HTTP reales, contra PostgreSQL con Testcontainers — sin ningún mock de por medio. Confirma que todas las capas encajan entre sí, incluido el cálculo real del total del pedido.

### Perfil de test aislado

Cuatro clases usan el perfil `test` (`@ActiveProfiles("test")`): `GestionDePedidosApiApplicationTests`, `PedidoIntegrationTest`, `PedidoRepositoryTest` y `ProductoRepositoryTest`. Todas comparten `src/test/resources/application-test.properties` (`spring.jpa.hibernate.ddl-auto=create`), y la base de datos PostgreSQL la levanta Testcontainers (`postgres:17-alpine`, versiones gestionadas por el BOM de Spring Boot) mediante una configuración `@TestConfiguration` con `@ServiceConnection`. La suite completa no depende de una base de datos local ni de variables de entorno: solo necesita el demonio de Docker en marcha, y es reproducible en cualquier máquina.

La excepción es `MigracionFlywayTest`: reactiva `spring.flyway.enabled=true` y `ddl-auto=validate` con `@SpringBootTest(properties=...)` para comprobar que V1+V2 migran y validan contra un contenedor vacío. No puede hacerlo en el perfil compartido porque la V2 siembra el catálogo y chocaría con los datos de los tests.

### Cómo ejecutarlos

Desde IntelliJ, botón derecho sobre `src/test/java` → `Run 'All Tests'`, o desde terminal (con el demonio de Docker en marcha):

```bash
mvn test                      # desde Backend/
mvn -f Backend/pom.xml test   # desde la raíz del repositorio
```

---

