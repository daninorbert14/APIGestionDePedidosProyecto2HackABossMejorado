# Gestión de Pedidos API

[![CI](https://github.com/daninorbert14/APIGestionDePedidosProyecto2HackABossMejorado/actions/workflows/ci.yml/badge.svg)](https://github.com/daninorbert14/APIGestionDePedidosProyecto2HackABossMejorado/actions/workflows/ci.yml)

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

**Backend**: Java 17, Spring Boot 4.1, Spring Web, Spring Data JPA, Hibernate, PostgreSQL con migraciones Flyway, Bean Validation, Lombok, Swagger / SpringDoc OpenAPI. Tests con JUnit 5, Mockito y Testcontainers.

**Frontend**: React 19, Vite 8, React Router 7, Sonner, Lucide (iconos), ESLint, CSS BEM, tokens unificados.

**Infraestructura**: Docker y Docker Compose (imágenes multietapa; el frontend se sirve con nginx), GitHub Actions (CI y publicación de imágenes en GHCR) y Dependabot.

## Requisitos previos

- **Docker** con Docker Compose: imprescindible para el modo "todo en Docker" y para los tests del backend (Testcontainers).
- Solo si ejecutas backend y frontend desde el IDE: **Java 17**, **Maven** (o el wrapper `Backend/mvnw`) y **Node 22**.

## Ejecutar con un solo comando

```bash
# Modo "todo en Docker" (levantar base de datos, backend y frontend):
#   1) Copia la configuración de ejemplo:
#      cp .env.example .env
#      # Define DB_PASSWORD en el .env creado
#   2) Arranca el sistema:
#      docker compose up --build
#   (Flyway crea el esquema y carga el catálogo de ejemplo en el primer arranque)

# Modo "solo base de datos" (base de datos en Docker, backend y frontend desde el IDE):
#   docker compose up --wait db

# Tests del backend (necesitan el demonio de Docker en marcha):
#   mvn -f Backend/pom.xml test
```

## Puertos

- Por defecto: **5432** (base de datos, solo 127.0.0.1), **8080** (backend), **8081** (frontend).
- Se pueden cambiar editando el archivo `.env` real en la raíz (nunca `.env.example`).
- **ADVERTENCIA**: El backend del IDE y el de Docker comparten el puerto 8080; no pueden estar activos a la vez.

## Configuración

1. **Primera vez**: copia `.env.example` a `.env` y define una contraseña `DB_PASSWORD`.
2. El archivo `.env` real queda fuera de git (ignorado por `.gitignore`); la configuración de ejemplo `.env.example` sí está versionada.
3. Si el backend del IDE ya está en marcha en el puerto 8080, `docker compose up` fallará por conflicto de puerto.
4. Las contraseñas con caracteres especiales no se garantizan; se recomienda una contraseña sencilla.
5. Cambiar la versión mayor de PostgreSQL sobre datos existentes impide que la base arranque; hay que borrar el volumen y empezar de cero.
6. Si la contraseña de `.env` es distinta a la de una base de datos ya creada, el backend fallará al autenticarse; hay que borrar los datos (`docker compose down -v`) o cambiarla dentro de la base.
7. El primer arranque construye imágenes y descarga dependencias: es lento y necesita internet.
8. `VITE_API_URL` se fija al construir la imagen del frontend; termina en `/api` y se pasa como argumento de construcción.
9. `docker compose down -v` también necesita el archivo `.env` para funcionar; no lo elimine después del primer arranque si quiere conservar los datos.
10. Los dos modos comparten los datos de la base de datos: lo guardado con uno se ve desde el otro.
11. **Datos iniciales**: al arrancar el backend, Flyway aplica las migraciones de `Backend/src/main/resources/db/migration/`: `V1__crear_esquema.sql` crea las tablas y `V2__catalogo_ejemplo.sql` carga un catálogo de ejemplo (terminales, categorías y productos). Hibernate solo valida el esquema; no lo modifica.

## Documentación y código

- **Swagger UI**: http://localhost:8080/doc/swagger-ui.html (después de arrancar el backend)
- **Colección Postman**: `Backend/postman/` — `API de gestión de pedidos - Hamburguesería.postman_collection.json`
- **Código fuente**: [Backend/](Backend/README.md) (Java 17, Spring Boot) y [Frontend/](Frontend/README.md) (React 19, Vite)
- **Especificaciones**: la carpeta [`specs/`](specs/) contiene una especificación por cambio (`spec.md`, `plan.md` y `tasks.md`); las reglas generales del proyecto están en [`docs/constitution.md`](docs/constitution.md).

## Integración continua

El repositorio tiene un flujo de GitHub Actions (`.github/workflows/ci.yml`) que se dispara en cada pull request hacia `main` (incluidos los drafts), en cada push a `main` y manualmente con `workflow_dispatch`.

**Qué verifica el flujo** (tres comprobaciones que corren en paralelo):

- **Backend**: compila y pasa todas las pruebas (`mvn -B -f Backend/pom.xml test`, con Testcontainers; no necesita variables `DB_*`).
- **Frontend**: instalación reproducible desde el lockfile (`npm ci`), lint (`npm run lint`) y build (`npm run build`).
- **Docker**: validez del `docker-compose.yml` (incluido que la contraseña siga siendo obligatoria) y construcción de las dos imágenes (backend y frontend), sin publicarlas.

**Publicación de imágenes**: la subida a GHCR ocurre **solo en un push verde a `main`**, cuando los tres trabajos anteriores terminan bien. Se publican las dos imágenes con la etiqueta del commit y con `latest` (este último solo si el commit sigue siendo la punta de `main`). **Nunca** se publica en pull requests ni en una ejecución manual.

**Pasos manuales** (solo la primera vez):

1. Activar GitHub Actions en el fork (pestaña *Actions* → *Enable actions on this repository*), una sola vez.
2. Tras la primera publicación, hacer públicos **a mano los DOS paquetes** de imágenes (backend y frontend) desde la pestaña *Packages* del repositorio; hasta entonces permanecen privados.

> **AVISO — no hacer `docker push` manual a ghcr.io antes de la primera publicación del CI**: una publicación manual crearía los paquetes fuera del flujo, sin pasar las comprobaciones, y condiciona la primera ejecución. Hay que esperar a que el flujo publique por sí mismo.

> **AVISO sobre la imagen del frontend**: la imagen del frontend que construye el CI lleva incrustada `http://localhost:8080/api`, la misma dirección por defecto que usa Docker Compose; por eso solo es válida en local con Docker Compose. Una eventual demo se construirá aparte, con su dirección pública.

## Dependencias (Dependabot)

`.github/dependabot.yml` vigila cinco ecosistemas: `npm` (Frontend), `maven` (Backend), `github-actions`, `docker` (los dos Dockerfile) y `docker-compose`.

- **Cuándo**: semanal, los lunes a las 09:00 UTC.
- **Cuántos PRs**: uno por dependencia y máximo 3 abiertos por entrada.
- **Cooldown de 7 días**: no propone versiones publicadas hace menos de una semana (las actualizaciones de seguridad no se retrasan).
- **Qué ignora, y por qué** (cada regla está comentada en el propio archivo): `postgres`, porque su etiqueta debe coincidir con la de los tests con Testcontainers; la imagen de construcción `maven`, porque el JDK va dentro de su etiqueta (`3-eclipse-temurin-17`); y las versiones mayores de `node` y `eclipse-temurin`, porque el proyecto fija Node 22 y Java 17.
- **Si un PR de Dependabot falla en CI no se fusiona**: se arregla en su propia rama o se cierra ignorando esa dependencia.

El aviso de seguridad aceptado en dependencias de desarrollo (`picomatch`) está explicado en el [README del Frontend](Frontend/README.md#limitaciones-conocidas).

## Cómo se ha construido

El proyecto se desarrolla con un flujo de especificación previa (*spec-driven development*): cada cambio parte de una especificación en `specs/NNN-nombre/` (`spec.md`, `plan.md`, `tasks.md`) que se revisa antes de escribir código. `AGENTS.md` recoge las instrucciones para los agentes de IA que colaboran en el repositorio y `MEMORY.md` el estado y las decisiones del proyecto entre sesiones.

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