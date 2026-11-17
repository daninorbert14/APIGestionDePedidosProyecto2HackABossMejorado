# Plan 007 — Activar Dependabot

Spec: `specs/007-activar-dependabot/spec.md` (Estado: aprobada). Este plan describe el **cómo**; el qué está en la spec. Es una spec de **configuración y documentación**: el único fichero nuevo es `.github/dependabot.yml` y la única edición es `MEMORY.md`. No se tocan `Backend/`, `Frontend/`, `.github/workflows/ci.yml`, `docker-compose.yml` ni los Dockerfile (solo se leen). Ningún punto del plan cae en el «⚠️ Pregunta antes» de AGENTS.md: no se cambia ningún DTO, ni `data.sql`, ni el esquema de una entidad, ni se añade ninguna dependencia al proyecto (un fichero de configuración de GitHub no es una dependencia del build).

Depende de la spec 005 (etiqueta de PostgreSQL compartida con Testcontainers, RF-19) y de la spec 006 (el workflow que vigila github-actions y que validará cada PR de Dependabot).

## 1. Enfoque técnico

GitHub lee `.github/dependabot.yml` en cada push a la rama por defecto y cuando se pulsa «Check for updates» (Insights > Dependency graph > Dependabot). La raíz del fichero lleva `version: 2` (obligatorio) y un array `updates`: **cada entrada vigila un ecosistema en un directorio** concreto.

**Cinco ecosistemas → seis entradas**, porque el ecosistema `docker` se declara una vez por directorio que contenga un Dockerfile (Dependabot solo mira el Dockerfile del directorio indicado; en la raíz no hay ninguno):

| # | Ecosistema | `directory` | Qué vigila | Ignores |
|---|---|---|---|---|
| 1 | `npm` | `/Frontend` | `Frontend/package.json` + lock | ninguno (RF-8) |
| 2 | `maven` | `/Backend` | `Backend/pom.xml` | ninguno (ojo: los «maven» de RF-7 son la **imagen Docker** de construcción, no estas dependencias) |
| 3 | `github-actions` | `/` | `.github/workflows/*.yml` | ninguno (RF-8) |
| 4 | `docker` | `/Backend` | `Backend/Dockerfile` | `postgres` completo (RF-6); `maven` **completo** (RF-7, ver decisión 6.7); `eclipse-temurin` solo mayores (RF-7) |
| 5 | `docker` | `/Frontend` | `Frontend/Dockerfile` | `postgres` completo (RF-6); `node` solo mayores (RF-7) |
| 6 | `docker-compose` | `/` | `docker-compose.yml` (raíz) | `postgres` completo (RF-6) |

Claves comunes a las seis entradas:

- `schedule`: `interval: weekly`, `day: monday`, `time: "09:00"`, `timezone: "UTC"` → **RF-4**.
- `open-pull-requests-limit: 3` → **RF-5** (límite por entrada: una saturada no bloquea a las demás).
- **Sin la clave `groups`**: el comportamiento por defecto es un PR por dependencia → **RF-3**.
- `cooldown` con retraso por defecto de 7 días → **RF-4b** (decisión 6.5; la spec admite que no todos los ecosistemas admitan la opción).
- `ignore` solo en las tres entradas de imagen (**RF-6**, **RF-7**), y **cada ignore con comentario en el propio archivo, en español, con su motivo y cuándo retirarlo** (**RF-9**).

Esqueleto de referencia (**estado final**; T1 lo crea sin `cooldown` ni `ignore`, que añaden T2 y T3, y T8 ajusta el ignore de la imagen `maven` a total; el `schedule` puede escribirse en bloque, como aquí, o en una línea de mapa en línea — YAML válido en ambos casos):

```yaml
version: 2
# Dependabot — specs/007-activar-dependabot: vigila los cinco ecosistemas del proyecto.
# Semanal (lunes 09:00 UTC), máximo 3 PRs abiertos por entrada y un PR por dependencia.
updates:
  # ── npm: librerías del frontend ───────────────────────────────────────────────
  - package-ecosystem: "npm"
    directory: "/Frontend"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
      timezone: "UTC"
    open-pull-requests-limit: 3
    cooldown:
      default-days: 7        # RF-4b: no propone versiones publicadas hace < 7 días (las de seguridad no se retrasan)

  # ── maven: dependencias del backend ───────────────────────────────────────────
  # Sin ignores: los "maven" de RF-7 se refieren a la IMAGEN Docker de construcción.
  - package-ecosystem: "maven"
    directory: "/Backend"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
      timezone: "UTC"
    open-pull-requests-limit: 3
    cooldown:
      default-days: 7

  # ── github-actions: acciones del workflow de CI ───────────────────────────────
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
      timezone: "UTC"
    open-pull-requests-limit: 3
    cooldown:
      default-days: 7

  # ── docker: imágenes base de Backend/Dockerfile ───────────────────────────────
  - package-ecosystem: "docker"
    directory: "/Backend"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
      timezone: "UTC"
    open-pull-requests-limit: 3
    cooldown:
      default-days: 7
    ignore:
      # PostgreSQL: la etiqueta debe seguir coincidiendo con la de las pruebas con
      # Testcontainers (RF-19 de la spec 005) y Dependabot no puede cambiar las pruebas
      # a la vez. Hoy no hay PostgreSQL en este Dockerfile; el ignore cubre un futuro
      # FROM postgres (RF-6 menciona los Dockerfile). Retirar solo si las pruebas y el
      # archivo de imágenes se actualizan a la vez.
      - dependency-name: "postgres"
      # Imagen de construcción fijada en el proyecto (Java 17): ignore TOTAL, no solo
      # de mayores. El JDK va dentro de la etiqueta (p. ej. 3-eclipse-temurin-17) y
      # Dependabot interpreta la versión como 3, de modo que el filtro de mayores no
      # impedía proponer subir de Java 17 (comprobado en la verificación real: PR #20,
      # 3-eclipse-temurin-17 → 3-eclipse-temurin-26). Retirar si el proyecto sube de
      # Java 17 (RF-7).
      - dependency-name: "maven"
      # eclipse-temurin es la imagen de JRE (Java 17): solo los mayores.
      # Retirar si el proyecto sube de Java 17 (RF-7).
      - dependency-name: "eclipse-temurin"
        update-types: ["version-update:semver-major"]

  # ── docker: imágenes base de Frontend/Dockerfile ──────────────────────────────
  - package-ecosystem: "docker"
    directory: "/Frontend"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
      timezone: "UTC"
    open-pull-requests-limit: 3
    cooldown:
      default-days: 7
    ignore:
      # PostgreSQL: mismo motivo que en el resto de ficheros de imagen (RF-6).
      - dependency-name: "postgres"
      # Node fijado en el proyecto (Node 22): solo bloqueamos los mayores.
      # Retirar si el proyecto sube de versión de Node (RF-7).
      - dependency-name: "node"
        update-types: ["version-update:semver-major"]

  # ── docker-compose: imágenes del compose de la raíz ───────────────────────────
  - package-ecosystem: "docker-compose"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
      timezone: "UTC"
    open-pull-requests-limit: 3
    cooldown:
      default-days: 7
    ignore:
      # PostgreSQL completo (cualquier nivel): la etiqueta del compose debe coincidir
      # con la de las pruebas con Testcontainers (RF-19 de la spec 005) y Dependabot no
      # puede cambiar las pruebas a la vez. Retirar solo si se actualizan a la vez las
      # pruebas y este archivo (RF-6).
      - dependency-name: "postgres"
```

## 2. Archivos y responsabilidades

| Fichero | Acción | Responsabilidad | RF |
|---|---|---|---|
| `.github/dependabot.yml` | **crear** | Único archivo de configuración: `version: 2` + las seis entradas con `schedule`, `open-pull-requests-limit`, `cooldown` e `ignore` comentados en español (contenido en §1). | RF-1 a RF-9 |
| `MEMORY.md` | **editar** (manteniendo ≤ ~50 líneas, condensando lo que no aporte) | Decisión de Dependabot (ritmo, límites e ignores con su motivo — RF-9), procedimiento de PR con CI rojo (RF-10) y paso manual de alertas/actualizaciones de seguridad (RF-12). | RF-9, RF-10, RF-12 |

**No se tocan**: `.github/workflows/ci.yml`, `docker-compose.yml`, `Backend/**` (incluidos `Dockerfile` y `pom.xml`), `Frontend/**` (incluidos `Dockerfile` y `package.json`), `README.md` (la insignia queda fuera de alcance), `AGENTS.md`, `docs/`, `.env*`.

## 3. Cambios por capa

- **Backend (model, repository, service, controller, dto, exception)**: **no aplica** porque la spec no añade ni modifica funcionalidad de negocio ni endpoints; el requisito no funcional «no altera el comportamiento del backend» lo prohíbe. Cero ficheros en `Backend/src`.
- **Frontend (api, hooks, components, pages)**: **no aplica** porque no hay UI nueva (fuera de alcance la insignia/README) ni llamadas nuevas al API. Cero ficheros en `Frontend/src`.
- **Workflow de CI (`.github/workflows/ci.yml`)**: **no aplica** porque RF-11 se cumple tal cual (verificado por lectura en T5): los jobs que corren en PRs (`backend`, `frontend`, `docker`) no usan `secrets.*` y el workflow ya declara `permissions: contents: read`. Modificarlo sería «alterar el CI», que la spec prohíbe.

## 4. Contrato de la API e interfaz

- **Contrato de la API**: **no aplica** porque la spec no introduce rutas, códigos HTTP ni JSON de éxito/error (nada en `controller`, `dto` ni `exception`). El único «contrato» nuevo es el esquema del propio `dependabot.yml` (§1 y pseudocódigo de §5).
- **Interfaz**: **no aplica** porque no hay cambios visuales: sin páginas ni componentes nuevos, y la insignia/README queda fuera de alcance.

## 5. Comportamiento de la configuración (pseudocódigo)

```text
para cada entrada E de .github/dependabot.yml:          # 6 entradas, 5 ecosistemas
    # E fija: ecosistema, directorio, límite 3 y calendario (RF-1 a RF-5)

    CADA semana, lunes 09:00 UTC (y en «Check for updates»):          # RF-4
        para cada dependencia vigilar en E:
            nuevas = versiones publicadas hace ≥ 7 días               # RF-4b (cooldown)
            si la versión es de seguridad → proponerla igual          # RF-4b: sin retraso
            si la versión está en los ignores de E → NO proponerla    # RF-6 (postgres), RF-7 (maven completo; mayores de node y eclipse-temurin)
            si esa dependencia ya tiene un PR abierto → NO duplicar   # 1 PR por dependencia (RF-3)
            si E ya tiene 3 PRs abiertos → parar en E                 # RF-5 (por entrada)
            si E tiene < 3 PRs → abrir UN PR con UNA dependencia      # RF-3, sin agrupar

    # Un PR abierto cuyo CI falle: NO fusionar; se arregla en su propia rama
    # o se cierra con el comentario de Dependabot que ignora esa dependencia
    # o esa versión mayor (RF-10) → no se reabrirá con cada versión nueva.

    # Seguridad del repositorio (alertas y security updates): fuera del archivo,
    # paso manual del usuario, cuando quiera (RF-12).
```

## 6. Decisiones justificadas (con la alternativa descartada)

**6.1 Seis entradas para cinco ecosistemas; `maven` en `/Backend`.**
El ecosistema `docker` admite un directorio por Dockerfile, así que son dos entradas (`/Backend` y `/Frontend`).
*Descartada*: una única entrada `docker` en `/` — en la raíz no hay Dockerfile, Dependabot no vigilaría ninguno; y `maven` en `/` — el `pom.xml` vive en `Backend/`, en la raíz no hay manifiesto que leer.

**6.2 `open-pull-requests-limit: 3` explícito en cada entrada.**
*Descartado*: dejar el límite por defecto (5 propuestas) no cumple RF-5; `0` (solo seguridad) no cumple RF-4, que exige propuestas de versión semanales.

**6.3 Sin la clave `groups` (nada de agrupar).**
*Descartado*: cualquier regla de `groups` agruparía actualizaciones en un solo PR y rompería RF-3 (un PR por dependencia para atribuir el fallo del CI a la actualización concreta).

**6.4 Programación explícita (`day: monday`, `time: "09:00"`, `timezone: "UTC"`).**
*Descartada*: dejar la programación por defecto de Dependabot no garantiza el lunes a las 09:00 UTC que exige RF-4.

**6.5 `cooldown: default-days: 7` en las seis entradas.**
Es la única opción que garantiza «al menos 7 días publicada» (RF-4b); las actualizaciones de seguridad no se retrasan.
*Descartadas*: `versioning-strategy` (solo cambia cómo se fija la versión en el manifiesto, no retrasa la propuesta) y «fiarse del calendario semanal» (el retraso sería de 0 a 7 días, no ≥ 7). *Nota*: la spec lo admite explícitamente («en los ecosistemas que admiten esa opción»); si Dependabot rechaza `cooldown` en algún ecosistema, se retira **solo en esa entrada**, se comenta el motivo en el propio archivo y se anota en `MEMORY.md`. Ese rechazo solo se ve en GitHub, en T7.

**6.6 `postgres` ignorado en las tres entradas de imagen (docker ×2 y docker-compose).**
*Descartada*: ponerlo solo en `docker-compose` — hoy es el único archivo donde aparece, pero RF-6 declara el alcance «el docker-compose de la raíz **y los Dockerfile**»: sin el ignore ahí, un futuro `FROM postgres` se propondría y desincronizaría la etiqueta de Testcontainers. Es la mínima que cubre el alcance que RF-6 declara (RNF «excepciones mínimas») y cada comentario explica que hoy solo aplica al compose. *Descartada también*: el ignore en `npm`, `maven` o `github-actions` (imposible que aparezca ahí, no aporta).

**6.7 Ignore **total** de la imagen `maven`; `update-types: version-update:semver-major` en `node` y `eclipse-temurin`.**
La imagen `maven` se ignora de **cualquier nivel** (RF-7 actualizado) porque el JDK va dentro de su etiqueta (`maven:3-eclipse-temurin-17`) y Dependabot interpreta la versión como `3`: el filtro `semver-major` no impedía proponer subir de Java 17 — comprobado en la primera ejecución real, con el PR #20 (de `3-eclipse-temurin-17` a `3-eclipse-temurin-26`), ya cerrado por el usuario con `@dependabot ignore this dependency`. Es la excepción mínima que evita esa actualización (RNF «excepciones mínimas»). `node` y `eclipse-temurin` siguen **solo-mayores** porque su versión sí expresa el salto de Node 22 / Java 17: sus parches y actualizaciones de seguridad siguen interesando. `nginx` queda **sin ignore** porque RF-7 no lo menciona. Ojo: en la entrada `maven` (dependencias del backend) **no** hay ningún ignore: los «maven» de RF-7 son la imagen `maven:3-eclipse-temurin-17`.
*Descartado*: mantener `semver-major` en la imagen `maven` (lo que decía este plan en su primera versión) — ya no sirve, como demostró el propio PR #20; *descartado también*: ignore total de `node` y `eclipse-temurin` — bloquearía parches y actualizaciones de seguridad de esas imágenes que sí interesan.

**6.8 Comentarios en español en el propio fichero, uno por ignore, con motivo y cuándo retirarlo.**
*Descartada*: documentar los motivos solo en `MEMORY.md` — RF-9 exige el comentario **en el propio archivo** (HU-5: quien lo lea en el futuro no lo quite sin saber por qué).

**6.9 No tocar `.github/workflows/ci.yml`.**
*Descartada*: añadir permisos extra o usar `pull_request_target` para RF-11 — los jobs que corren en PRs no usan secretos y el workflow ya declara permisos mínimos; tocarlo sería alterar el CI, fuera de alcance. La prueba definitiva (PR real con token de solo lectura) es de T7.

**6.10 Validación local sin añadir dependencias.**
*Descartados*: actionlint (solo valida workflows de GitHub Actions, no `dependabot.yml`), PyYAML (no instalado — ver `MEMORY.md`) y añadir un linter al proyecto (viola el RNF «no añade dependencias»). Se valida la sintaxis con un parser YAML ya presente en la máquina (p. ej. Ruby con su librería estándar `yaml`); la validación semántica **autoritativa** es la del propio Dependabot (T7: «Check for updates»), sin herramientas externas a él.

## 7. Estrategia de tests y verificación

### 7.1 Automatizable en local (sin GitHub)

1. **Sintaxis del YAML** (RNF «formato canónico, sin dependencias externas al propio Dependabot»; decisión 6.10): parser YAML ya presente en la máquina, sin instalar nada. → cubre el RNF de formato.
2. **Checklist de conformidad por lectura** (tarea T4), un punto por RF: 5 ecosistemas en 6 entradas con sus directorios (**RF-1**, **RF-2**); sin `groups` (**RF-3**); `schedule` lunes 09:00 UTC (**RF-4**); `cooldown` de 7 días (**RF-4b**); límite 3 en las seis entradas (**RF-5**); `postgres` completo en las tres entradas de imagen (**RF-6**); `maven` **completo** (sin `update-types`) y solo mayores de `node` y `eclipse-temurin`, cada uno solo en la entrada donde su imagen aparece (**RF-7**; punto actualizado en T8 tras el cambio de RF-7); **cero** ignores de ESLint/minimatch — comprobación negativa con búsqueda en el archivo (**RF-8**); todos los comentarios en español y cada ignore con motivo y cuándo retirarlo (**RF-9**).
3. **No-regresión** (tarea T5): `mvn -f Backend/pom.xml test` en verde (con Docker en marcha; 124 pruebas), `npm --prefix Frontend run build` y `npm --prefix Frontend run lint` en verde → criterio de finalización «las pruebas siguen en verde» y RNF «no altera el comportamiento del backend, del frontend ni del CI».
4. **Alcance del cambio** (tarea T5): `git status` / `git diff --name-only` muestra solo `.github/dependabot.yml`, `MEMORY.md` y `specs/007-activar-dependabot/`; `git diff` vacío en `.github/workflows/ci.yml`, `docker-compose.yml`, los Dockerfile, `Backend/` y `Frontend/` → RNF «solo decide qué propuestas se emite».
5. **RF-11 por lectura** (tarea T5): en `ci.yml`, `secrets.*` aparece únicamente en el job `publish`, que lleva `if: github.event_name == 'push' && github.ref == 'refs/heads/main'` → nunca corre en PRs; los jobs que corren en `pull_request` no usan secretos y el workflow declara `permissions: contents: read`. → **RF-11** (parte local; la prueba real es T7).

### 7.2 Manual (solo el usuario: no podemos abrir el PR de Dependabot nosotros)

| Paso (usuario) | Qué comprueba | RF |
|---|---|---|
| Tras fusionar: Insights > Dependency graph > Dependabot → «Check for updates» sin errores de configuración | validación semántica del archivo (incluida la admisión de `cooldown` en cada ecosistema) | RF-1, RF-4, RF-4b, RF-5 |
| Cuando llegue un PR genuino de Dependabot: su ejecución de CI pasa | token de solo lectura, sin secretos | RF-11 |
| Observar el flujo real (cuando haya suficientes versiones nuevas) | un PR por dependencia; ≤ 3 abiertos por entrada; y, **si una entrada queda con más propuestas abiertas que el límite (p. ej. `github-actions` ×4 en la primera ejecución), antes de dar por cumplido RF-5: comprobar que la configuración activa en GitHub es la versionada en el repositorio y observar que una ejecución posterior no abre nuevas propuestas en esa entrada hasta bajar del límite** | RF-3, RF-5 |
| Observar que no vuelve a abrirse ninguna propuesta de la imagen `maven` (la #20 se cerró con `@dependabot ignore this dependency` y el ignore es ahora total) | excepción de la imagen `maven` completa | RF-7 |
| Si aparece un PR con CI rojo: no fusionar; arreglar en su rama o cerrar con el comentario de ignorar | procedimiento de revisión | RF-10 |
| Cuando el usuario quiera: activar alertas y actualizaciones de seguridad del repositorio | paso manual documentado en `MEMORY.md` | RF-12 |

## 8. Riesgos y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| GitHub rechaza `cooldown` en algún ecosistema (la spec ya admite «en los ecosistemas que admiten esa opción») | T7 lo detecta en «Check for updates»: se retira solo en esa entrada, se comenta el motivo en el archivo y se anota en `MEMORY.md`. |
| El ecosistema `docker-compose` no está soportado o lleva otro nombre | Mismo procedimiento en T7; los otros cuatro ecosistemas siguen vigentes. |
| Dependabot solo valida el archivo tras fusionar → errores de configuración tardíos | Checklist exhaustivo en T4 + T7 como validación autoritativa. |
| Tags flotantes: Docker/Compose no propone nada | Previsto por la spec (caso límite): no es un fallo; la imagen se actualiza al reconstruir. |
| Una entrada llega a 3 PRs y se atasca | Previsto por RF-5; cerrar/revisar los PRs abiertos de esa entrada. |
| Un PR de Dependabot con CI rojo acumula propuestas | Procedimiento RF-10 documentado en `MEMORY.md` (T6) y aplicado en T7. |
| `MEMORY.md` supera ~50 líneas al añadir la entrada | Condensar/resumir al escribirlo (regla de AGENTS.md). |

## 9. Cobertura de RF (resumen)

- **RF-1, RF-2** → seis entradas con sus directorios (§1) → T1, T4.
- **RF-3** → sin `groups` (un PR por dependencia) → T1, T4, observación en T7.
- **RF-4** → `schedule` semanal lunes 09:00 UTC → T1, T4, T7.
- **RF-4b** → `cooldown` de 7 días → T2, T4, T7 (fallback si un ecosistema no lo admite).
- **RF-5** → `open-pull-requests-limit: 3` por entrada → T1, T4, observación en T7 (con la cláusula de la spec: si una entrada queda por encima del límite, comprobar que la configuración activa en GitHub es la versionada en el repositorio y que una ejecución posterior no abre nuevas propuestas hasta bajar del límite).
- **RF-6** → ignore `postgres` completo en las tres entradas de imagen → T3, T4.
- **RF-7** → ignore **total** de `maven` (imagen) y `semver-major` de `node` y `eclipse-temurin` (solo imágenes) → T3, T4 (versión original), **T8** (ajuste del ignore de `maven` a total + comentario, checklist y memoria) y observación en T7.
- **RF-8** → comprobación negativa: ningún ignore de ESLint ni minimatch → T4.
- **RF-9** → comentarios en español en el propio archivo (T3) + decisión recogida en `MEMORY.md` (T6) y actualizada con el ignore total de `maven` (T8).
- **RF-10** → procedimiento documentado en `MEMORY.md` (T6) + aplicación real con un PR rojo (T7).
- **RF-11** → lectura de `ci.yml` sin secretos en los jobs de PR (T5) + PR real con CI verde (T7).
- **RF-12** → entrada en `MEMORY.md` (T6) + activación manual del repositorio (T7).
