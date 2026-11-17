# Spec 007 — Activar Dependabot

Estado: aprobada

## Contexto y objetivo

El proyecto depende de terceros en cinco frentes: las librerías del frontend (npm), las de
backend (Maven), las acciones de GitHub del workflow de CI, las imágenes base de los
Dockerfile y las imágenes que usa el docker-compose de la raíz. Hoy esas dependencias se
actualizan a mano y tarde, con el riesgo de acumular desactualizaciones difíciles de
detectar. El objetivo es que Dependabot vigile los cinco ecosistemas y abra propuestas de
actualización de forma controlada —semanal, sin saturar el repositorio y sin romper las
decisiones ya tomadas del proyecto— para mantener el sistema actualizado con un esfuerzo
de revisión bajo.

Depende de la spec 005 (Docker) por la etiqueta de PostgreSQL compartida con Testcontainers
(RF-19) y de la spec 006 (CI), cuyo workflow es uno de los ecosistemas a vigilar y el que
valida cada propuesta.

## Usuarios

- Desarrollador (el autor o quien mantenga el repositorio) que revisa y fusiona las
  propuestas de actualización.
- Persona que evalúa el proyecto y ve un repositorio mantenido, con dependencias al día.

## Historias de usuario

- HU-1. Como desarrollador, quiero que Dependabot vigile todas las dependencias del
  proyecto para no tener que comprobar versiones a mano.
- HU-2. Como desarrollador, quiero que las propuestas lleguen de forma ordenada (un día
  predecible y un número limitado) para poder revisarlas sin agobio.
- HU-3. Como desarrollador, quiero un PR por dependencia para saber exactamente qué
  actualización rompe el CI si algo falla.
- HU-4. Como desarrollador, quiero que la configuración no proponga cambios que rompan
  decisiones ya tomadas (versión de PostgreSQL, Node, Java) para evitar falsos positivos.
- HU-5. Como desarrollador, quiero que cada excepción de la configuración explique su
  motivo para que quien la lea en el futuro no la quite sin saber por qué está.

## Definiciones

- **Ecosistema**: cada bloque de la configuración que vigila un ecosistema en una ubicación
  (por ejemplo, npm en /Frontend).
- **Ignore (excepción)**: regla que impide a Dependabot proponer una actualización concreta.
- **Propuesta / PR de Dependabot**: rama y solicitud de fusión que abre Dependabot al
  detectar una versión nueva de una dependencia sin actualizar.

## Requisitos funcionales

### Alcance de la vigilancia

- RF-1: EL SISTEMA vigilará los cinco ecosistemas del proyecto: las dependencias npm del
  frontend, las dependencias Maven del backend, las acciones de GitHub usadas por el workflow 
  de Ci, las imágenes base de los Dockerfile y las imágenes del docker-compose de la raíz.
- RF-2: EL SISTEMA incluirá en la configuración las entradas de Docker (los Dockerfile) y
  de Docker Compose (el docker-compose de la raíz).
- RF-3: CUANDO Dependabot abra una propuesta, EL SISTEMA la limitará a una única
  dependencia por PR, sin agrupar actualizaciones, de modo que si el CI falla se pueda
  atribuir el fallo a esa actualización concreta.

### Frecuencia y ritmo

- RF-4: EL SISTEMA comprobará las actualizaciones de versión una vez por semana, los lunes
  a las 09:00 (UTC). Las actualizaciones de seguridad no están sujetas a este calendario.
- RF-4b: EL SISTEMA no propondrá una versión hasta que lleve publicada al menos 7 días.
  Las actualizaciones de seguridad no se retrasan.
- RF-5: EL SISTEMA mantendrá como máximo 3 propuestas de versión abiertas por cada entrada
  de la configuración; SI una entrada alcanza ese límite, ENTONCES no abrirá nuevas propuestas
  en ella hasta que se cierre alguna.

### Excepciones (ignores) ya decididas

- RF-6: SI hay una versión nueva de la imagen de PostgreSQL en los archivos que Dependabot
  vigila (el docker-compose de la raíz y los Dockerfile), ENTONCES EL SISTEMA no propondrá
  esa actualización, para que la etiqueta siga coincidiendo con la de las pruebas con
  Testcontainers (RF-19 de la spec 005), que Dependabot no ve. El ignore es deliberadamente
  más estricto que RF-19: como Dependabot no puede cambiar a la vez la etiqueta de las pruebas,
  cualquier cambio de versión en el compose las desincronizaría.
- RF-7: EL SISTEMA no propondrá actualizaciones mayores de las imágenes de node,
  eclipse-temurin ni maven (la de construcción del backend), porque el proyecto está fijado
  en Node 22 y Java 17.
- RF-8: No existe ninguna excepción para ESLint ni para minimatch.
- RF-9: Cada excepción de la configuración llevará un comentario, en el propio archivo,
  con su motivo y cuándo retirarla (por ejemplo, «retirar si el proyecto sube de Java 17»).
  Motivos: PostgreSQL debe coincidir con Testcontainers (RF-19 de la spec 005); Node, eclipse-temurin
  y maven (imagen de construcción) están fijados por el proyecto. La decisión quedará recogida
  en la memoria del proyecto.

### Revisiones y fallos

- RF-10: SI el CI falla en un PR de Dependabot, ENTONCES ese PR no se fusionará: se
  revisará a mano y se arreglará en su propia rama o se cerrará con el comentario de
  Dependabot que indique ignorar esa dependencia o esa versión mayor, para que no se
  reabra con cada versión nueva.
- RF-11: CUANDO Dependabot abra un pull request, EL SISTEMA ejecutará el CI sobre él
  con los permisos que reciben esos PR (token de solo lectura, sin secretos), y debe
  poder pasar sin ellos.

### Activación en el repositorio

- RF-12: EL SISTEMA documentará, en la memoria del proyecto (MEMORY.md), que activar
  las alertas y las actualizaciones de seguridad del repositorio es un paso manual e
  independiente del archivo de configuración, porque en un fork vienen desactivadas.

## Requisitos no funcionales

- La configuración queda versionada en el repositorio, en su formato canónico, y se
  valida sin dependencias externas al propio Dependabot.
- Los comentarios de la configuración están en español, como el resto del proyecto.
- La configuración no añade dependencias al proyecto ni altera el comportamiento del
  backend, del frontend ni del CI: solo decide qué propuestas se emiten.
- Las excepciones son las mínimas necesarias: cualquier ignore nuevo exige justificación
  con su motivo en el archivo (RF-9).
- La verificación se hace contra el repositorio real (fork) con un PR de Dependabot
  genuino, no solo con la validación local del archivo.

## Casos límite

- Un PR de Dependabot hace fallar el CI: no se fusiona; se arregla en su rama o se cierra
  con el comentario de ignorar (RF-10).
- Un ecosistema llega a 3 PRs abiertos: no se emiten más hasta cerrar alguno (RF-5); el
  límite es por entrada de la configuración, de modo que una entrada saturada no bloquea a las demás.
- Dependabot no propone nada en Docker/Compose por las etiquetas flotantes: es lo
  previsto, no un fallo (RF-2); la imagen se actualizará al reconstruir.
- Propuesta de una nueva versión de PostgreSQL (de cualquier nivel): no se emite (RF-6).
  Propuesta de una versión mayor de Node, eclipse-temurin o maven: no se emite (RF-7).
- Fallo de minimatch/ESLint: ninguna excepción lo cubre; se espera a la versión corregida
  del fabricante (RF-8).
- El usuario configura las alertas de seguridad del repositorio como un paso manual e
  independiente del archivo, cuando quiera: hasta entonces las alertas siguen desactivadas
  en el fork (RF-12).
- Un PR de Dependabot no tiene acceso a secretos (token de solo lectura): el CI debe
  pasar igual (RF-11); si el workflow necesitara un secreto, fallaría en estos PR.
- Varias dependencias desactualizadas a la vez: se traducen en varios PRs, uno por
  dependencia, dentro del límite por entrada (RF-3, RF-5).

## Fuera de alcance

- Auto-merge de PRs de Dependabot.
- Renovate u otra herramienta de actualización automática.
- Insignia o sección de README sobre Dependabot.
- La configuración de seguridad del repositorio (alertas y actualizaciones de seguridad)
  como parte del archivo: se documenta como paso manual e independiente del archivo,
  que el usuario hace cuando quiera.
- Fijar las etiquetas flotantes de los Dockerfile: es otra decisión que afecta a la spec
  005 y queda fuera de esta spec; los tags se dejan como están.
- Resolver el aviso de minimatch (ESLint 10): se hará cuando salga la versión corregida,
  sin excepciones en la configuración (RF-8).
- Agrupar actualizaciones en un solo PR, programar otros días o cambiar el límite de PRs
  fuera de lo decidido en esta spec.

## Criterios de finalización

- El archivo de configuración existe, está versionado y cubre los cinco ecosistemas
  (RF-1), con el límite de 3 PRs por entrada (RF-5), un PR por dependencia (RF-3) y
  programación semanal los lunes (RF-4).
- Cada excepción lleva su comentario con el motivo en el propio archivo (RF-9).
- Tras fusionar el archivo, el usuario fuerza una comprobación («Check for updates» en
  Insights > Dependency graph > Dependabot) y, cuando llega un PR real, su ejecución del CI
  pasa con el token de solo lectura y sin secretos.
- No existe ninguna excepción para ESLint ni para minimatch (RF-8) y sí para PostgreSQL
  completo y para los mayores de node, eclipse-temurin y maven (RF-6, RF-7).
- La documentación del paso manual de seguridad del repositorio está redactada (RF-12) y
  la decisión queda recogida en la memoria del proyecto (RF-9).
- Las pruebas del backend y el build/lint del frontend siguen en verde: la configuración
  no altera el código.
- El archivo declara un retraso de 7 días antes de proponer una versión nueva, en los ecosistemas
  que admiten esa opción.

## Dudas abiertas

- Ninguna: todas las decisiones de la entrevista (alcance, frecuencia, ritmo, excepciones
  y casos límite) quedan reflejadas en los RF anteriores.
