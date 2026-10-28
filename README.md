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
# Modo "todo en Docker" (levantar base de datos, backend y frontend):
#   1) Copia la configuración de ejemplo:
#      cp .env.example .env
#      # Define DB_PASSWORD en el .env creado
#   2) Arranca el sistema:
#      docker compose up --build

# Modo "solo base de datos" (base de datos en Docker, backend y frontend desde el IDE):
#   docker compose up --wait db

# Tests del backend (necesitan el demonio de Docker en marcha):
#   mvn -f Backend/pom.xml test
```

## Puertos

- Por defecto: **5432** (base de datos, solo 127.0.0.1), **8080** (backend), **8081** (frontend).
- Se pueden cambiar editando el archivo `.env` real en la raíz (nunca `.env.example`).
- **ADVERTENCIA**: El backend del IDE y el de Docker comparten el puerto 8080; no pueden estar activos a la vez (RF-16).

## Configuración

1. **Primera vez**: copia `.env.example` a `.env` y define una contraseña `DB_PASSWORD`.
2. El archivo `.env` real queda fuera de git (ignorado por `.gitignore`); la configuración de ejemplo `.env.example` sí está versionada.
3. Si el backend del IDE ya está en marcha en el puerto 8080, `docker compose up` fallará por conflicto de puerto.
4. Las contraseñas con caracteres especiales no se garantizan; se recomienda una contraseña sencilla.
5. Cambiar la versión mayor de PostgreSQL sobre datos existentes impide que la base arranque; hay que borrar el volumen y empezar de cero (RF-20).
6. Si la contraseña de `.env` es distinta a la de una base de datos ya creada, el backend fallará al autenticarse; hay que borrar los datos (`docker compose down -v`) o cambiarla dentro de la base (RF-21).
7. El primer arranque construye imágenes y descarga dependencias: es lento y necesita internet.
8. `VITE_API_URL` se fija al construir la imagen del frontend; termina en `/api` y se pasa como argumento de construcción (RF-15).
9. `docker compose down -v` también necesita el archivo `.env` para funcionar; no lo elimine después del primer arranque si quiere conservar los datos.
10. Los dos modos comparten los datos de la base de datos: lo guardado con uno se ve desde el otro.

## Documentación y código

- **Swagger UI**: http://localhost:8080/doc/swagger-ui.html (después de arrancar el backend)
- **Colección Postman**: `Backend/postman/` — `API de gestión de pedidos - Hamburguesería.postman_collection.json`
- **Código fuente**: [Backend/](Backend/README.md) (Java 17, Spring Boot) y [Frontend/](Frontend/README.md) (React 19, Vite)

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

## Autores

Laura Arias, Daniel Norbert, Rafael Porcel  
Proyecto desarrollado como parte de un bootcamp de programación full stack y posteriormente ampliado por Daniel Norbert.