# Gestión de Pedidos API

API REST y aplicación web de terminales de autoservicio: Terminal → Cocina → Recogida.

## Demo

Sin demo pública aún; el proyecto está en fase de desarrollo activo. Capturas y vídeos próximos.

## Arquitectura

```text
Frontend (React + Vite)                Backend (Spring Boot)
         │                                  │
         └── HTTP ──────────────────────────┘
                                    │
                              PostgreSQL
```

## Stack, de un vistazo

**Backend**: Java 17, Spring Boot 4.1.0, Spring Web, Spring Data JPA, Hibernate, PostgreSQL, Lombok, Swagger / SpringDoc OpenAPI.

**Frontend**: React 19, Vite 8, React Router 7, Sonner, Lucide (iconos), CSS BEM, tokens unificados.

## Ejecutar con un solo comando

```bash
# Backend
mvn -f Backend/pom.xml spring-boot:run

# Frontend
npm --prefix Frontend run dev

# Con Docker (cuando esté listo):
#   docker compose up -d  (o arrancar el daemon de Docker Desktop para tests con Testcontainers)
```

## Documentación y código

- **Swagger UI**: http://localhost:8080/doc/swagger-ui.html (después de arrancar el backend)
- **Colección Postman**: `Backend/postman/` — `API de gestión de pedidos - Hamburguesería.postman_collection.json`
- **Código fuente**: `Backend/` (Java 17, Spring Boot) y `Frontend/` (React 19, Vite)

## Autores

Laura Arias, Daniel Norbert, Rafael Porcel  
Proyecto desarrollado como parte de un bootcamp de programación full stack.

## Mejoras futuras (consolidado)

- Filtros en base de datos (push filtering en lugar de Java streams)
- Paginación en listados de productos y pedidos
- Autenticación con Spring Security + JWT (roles terminal/cocina/recogida)
- Gestión de imágenes de producto (S3 o almacenamiento similar)
- Mejoras de accesibilidad
- Diseño responsive avanzado
- Dashboard con estadísticas de ventas
- Tests unitarios con Vitest (frontend)
- Migración del polling de 15 s a WebSockets para actualización en tiempo real
- Mejoras de manejo de errores HTTP diferenciados
- Persistencia de terminal seleccionada mediante localStorage