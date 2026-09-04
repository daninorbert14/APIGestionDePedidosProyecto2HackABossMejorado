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
- MySQL
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
- Eliminar productos de forma total o parcial vía `?cantidad=N`
- Cambiar estado del pedido respetando el flujo definido
- Obtener pedido por código único generado automáticamente
- Listar pedidos con filtro opcional por estado, ordenados por fecha ascendente

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
DELETE /api/pedidos/{pedidoId}/productos/{productoId}?cantidad=2
PATCH  /api/pedidos/{pedidoId}/estado
GET    /api/pedidos/codigo/{codigo}
GET    /api/pedidos?estado=LISTO

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
  "error": "Datos de entrada inválidos",
  "detalles": [
    "nombre: El nombre no puede estar vacío",
    "precio: El precio debe ser mayor que 0"
  ]
}
```

---

## Datos de prueba

El proyecto incluye `data.sql` en `src/main/resources` con inserciones iniciales de:

- Terminales
- Categorías
- Productos
- Pedidos (opcional)

---

## Configuración del proyecto

En `application.properties`:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

Crea un archivo `.env` o configura las variables de entorno antes de ejecutar:

DB_URL=jdbc:mysql://localhost:3306/gestion_pedidos_bd?createDatabaseIfNotExist=true

DB_USERNAME=root

DB_PASSWORD=

> **Importante:** no subas credenciales reales al repositorio. Añade `.env` a tu `.gitignore`.

---

## Documentación API (Swagger)

Una vez ejecutado el proyecto:
http://localhost:8080/swagger-ui/index.html

### Colección Postman

En la carpeta postman/ de este repositorio encontrarás el archivo:
Gestion de pedidos - hamburgueseria.postman_collection.json. 

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

---

## Mejoras futuras

Funcionalidades identificadas como mejoras técnicas a implementar en futuras iteraciones:

- **Filtros en base de datos**: los listados actuales cargan todos los registros en memoria y filtran con streams de Java. Con volumen alto de datos conviene migrar a `findByActivoTrue()`, `@Query`, `Specification` o `Pageable` para que sea la base de datos quien filtre.

- **Productos más vendidos**: endpoint `GET /api/estadisticas/productos-mas-vendidos` que agregue las líneas de pedido por producto y devuelva un ranking.

- **Terminal más utilizada**: endpoint `GET /api/estadisticas/terminal-mas-utilizada` que cuente pedidos por terminal.

- **Paginación**: añadir `Pageable` a los listados de pedidos y productos para no devolver colecciones completas en entornos con muchos registros.

- **Autenticación**: proteger los endpoints de gestión (crear, actualizar, cambiar estado) con Spring Security y JWT, dejando públicos solo los de consulta desde terminal.

- **Gestión de imágenes**: el campo URL de foto en producto está previsto pero no implementado. Se podría integrar almacenamiento en S3 o similar.

---

## Autores

Laura Arias, Daniel Norbert y Rafael Porcel  
Proyecto desarrollado como parte de un bootcamp de programación full stack.