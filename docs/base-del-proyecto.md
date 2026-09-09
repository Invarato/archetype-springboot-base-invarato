# Base del proyecto

Memoria duradera del repo: **qué se decidió, por qué, y qué tropiezos ya hemos pagado**. Si el código y este
documento discrepan, gana el código — pero entonces actualiza el documento, porque significa que algo cambió
y nadie lo anotó.

Regla de uso: cuando cierres una decisión o te comas un tropiezo nuevo, **anótalo aquí**. Es lo que evita
volver a discutir lo mismo dentro de tres meses sin recordar los argumentos.

---

## Qué es este repo

**`archetype-springboot-base-invarato`** (`com.jarroba`, MIT, publicado en Maven Central) es el **arquetipo
Maven** que genera los microservicios Spring Boot de la casa. No es una aplicación: es la plantilla.

Dos criterios mandan sobre cualquier otro:

1. **Lo generado tiene que compilar y arrancar a la primera.** Un arquetipo que produce algo roto es peor que
   no tener arquetipo: se descubre tarde y hay que desandar el camino.
2. **Lo generado es ejemplo, no andamio.** Se lee y se copia. Un antipatrón en la plantilla se propaga a
   todos los servicios que salgan de aquí.

Destino de lo generado: microservicios distribuibles, contenedorizados y desplegables en Kubernetes.

---

## Decisiones tomadas

### D1 · Proyecto generado **multi-módulo** desde el principio (2026-09-09)

**Decisión.** El arquetipo genera un reactor, no un módulo suelto:

```
<artifactId>/
├── .mvn/maven.config          -Drevision por defecto (ver D3, y el gotcha G8)
├── pom.xml                    padre, packaging pom
├── app/                       <artifactId>-app · el microservicio Spring Boot
├── contract/                  <artifactId>-contract · openapi.json y, cuando haga falta,
│                              clientes Java/Python y stubs generados
├── compose-app.yml · Dockerfile · k8s/ · skaffold.yaml
```

**Por qué.** La necesidad real y repetida son **clientes Java y Python, stubs y el `openapi.json`**, que hoy
no tienen dónde vivir. Y el coste está muy mal repartido en el tiempo: montarlo al generar cuesta un pom y un
nivel de carpeta; hacerlo después es un refactor que toca `Dockerfile`, `compose`, `k8s`, `skaffold` y CI a
la vez. Se paga ahora, que es cuando es barato.

**Qué la reabriría.** Que en la práctica nadie llegue a usar `contract/` en varios proyectos seguidos.

### D2 · **Flyway** en lugar de Liquibase (2026-09-09)

**Decisión.** Las migraciones van con **Flyway** (`spring-boot-starter-flyway` + `flyway-database-postgresql`).

**Por qué.** Pesó la **licencia**: Liquibase 5.x dejó de ser open source y pasó a **FSL-1.1-ALv2**
(*Functional Source License*), que la OSI no reconoce; cada versión revierte a Apache-2.0 a los dos años.
Verificado en el `LICENSE.txt` del propio repo de Liquibase, no en un artículo. Este arquetipo es MIT, está
publicado en Central y es la base de proyectos potencialmente comerciales: heredar una dependencia
source-available a *todos* los servicios generados es una decisión que había que tomar mirándola, no
arrastrarla. **Flyway Community sigue siendo Apache-2.0** (verificado). Además es el consenso para
microservicios con base de datos por servicio y un solo motor, que es exactamente este caso.

**Qué se pierde, dicho claro.** El bootstrap **automático del primer changelog desde las entidades JPA** era
la ventaja diferencial de Liquibase (`liquibase-hibernate`, gratis) y en Flyway el equivalente
(`generate`/`diff`) es de pago. **No se renuncia al flujo, se sustituye**: está resuelto y documentado en
[migraciones.md](migraciones.md). Resumen: el `V1__init.sql` se vuelca desde el metadato de las entidades con
la exportación de esquema estándar de JPA, y a partir de ahí **manda Flyway** y las siguientes versiones se
escriben a mano.

**El sustituto del `diff` es mejor de lo que parece.** Sin herramienta de diff, el detector de deriva pasa a
ser `ddl-auto: validate` + un test de integración que corre Flyway sobre un Testcontainer y valida las
entidades contra el esquema resultante. Eso **falla en el build**, no en producción, y no depende de que
alguien se acuerde de lanzar un `diff`.

**Qué la reabriría.** Necesitar soportar varios motores de base de datos a la vez, o rollback real como
estrategia de despliegue.

### D3 · Versionado: `${revision}` + `flatten-maven-plugin` (2026-09-09)

**Decisión.** Se mantiene `<version>${revision}</version>` y se **añade `flatten-maven-plugin`**
(`flattenMode=resolveCiFriendliesOnly`, `updatePomFile=true`, `flatten` en `process-resources` y
`flatten.clean` en `clean`).

**Por qué.** Es el camino oficial de Maven para *CI-Friendly Versions*, el pom sigue siendo válido y legible
en el IDE, y **arregla un bug real**: sin flatten, el pom **instalado o publicado conserva el literal
`${revision}`** y quien consuma el artefacto no puede resolverlo. La documentación de Maven exige flatten
justo para `install`/`deploy`.

**Qué la reabriría.** Querer cero commits de release y versión derivada de tags de git: ahí la alternativa es
`maven-git-versioning-extension`. Se descartó ahora porque esconde la versión del pom y da fricción en IDEs.

### D4 · El **parent POM publicado** va en una segunda fase (2026-09-09)

**Contexto.** Un arquetipo Maven es de **un solo disparo**: genera y se olvida. Cuando mejoremos la
plantilla, los servicios **ya creados no se enteran nunca**. Es la crítica de fondo a los arquetipos frente a
alternativas tipo Copier, que sí pueden actualizar proyectos generados.

**Decisión.** La solución acordada es partir en dos: un **`com.jarroba:microservice-parent`** versionado y
publicado (BOM, versiones, plugins, perfiles, surefire/failsafe, flatten) del que hereden los proyectos
generados, dejando el arquetipo fino (estructura, ejemplo, infra). Subir el parent llevaría mejoras a
servicios existentes. **Pero primero** se deja el arquetipo verde y modernizado; el parent se aborda cuando
esto esté estable y publicado.

**Por qué en ese orden.** Extraer un parent desde una plantilla que hoy ni compila es construir sobre arena.

### D5 · Devcontainer: **un solo perfil, con Docker** (2026-09-09 — REVISA Y SUSTITUYE la decisión anterior)

**Decisión anterior (descartada).** Dos perfiles: `hardened` por defecto (sin Docker, `cap-drop=ALL`, sudo
solo para el firewall) y `with-docker` para lo que exigiera un demonio. Se mantuvo un día.

**Por qué se cayó.** Porque dejaba fuera justo lo que hay que comprobar. El proyecto generado necesita Docker
para sus tests de integración (Testcontainers) y para su `compose-app.yml`; un entorno donde solo se puede
compilar deja el `mvn verify` sin correr. Y el arquetipo se vende como *«descárgalo y trabaja con agentes»*:
si la caja donde se desarrolla no puede correr Docker, la promesa es falsa. El argumento no fue teórico — lo
levantó el propio uso, al quedar los `*IT` sin verificar tras el paso 3.

**Decisión.** **Un único devcontainer, con Docker-in-Docker**, tanto en este repo como en el que se copia a
los proyectos generados. Se elimina el mecanismo de sudo restringido: en un contenedor privilegiado
restringir sudo es teatro (se es root por otras vías) y un candado que no cierra pero estorba es peor que no
tenerlo, porque invita a confiar en él.

**Las alternativas, y por qué ninguna sirve hoy** (verificado, no de memoria):

| Alternativa | Peaje real |
|---|---|
| **DinD rootless** | Issue **abierto y sin asignar** en la spec de devcontainers (#479): fallan las escrituras al bind mount salvo corriendo como root dentro. Sin receta verificada de extremo a extremo; la validación con Testcontainers es de 2020. |
| **Podman rootless** | Funciona, pero exige `TESTCONTAINERS_RYUK_DISABLED=true` → se pierde la limpieza automática de contenedores. Más rarezas de red (su red por defecto es `podman`, no `bridge`) y de permisos del socket. |
| **docker-outside-of-docker** | El socket del anfitrión **es root en el anfitrión** y mezcla los contenedores efímeros de test con los de trabajo real. Movimiento lateral o peor, no una mejora. |
| **Testcontainers Cloud** | Sí evitaría el demonio local sin privilegios, pero es dependencia externa y cuenta de terceros. |

**La idea que ordena esto.** El contenedor **nunca fue la frontera real**: el repo va montado en
lectura/escritura y el token de `gh` vive dentro, con privilegios o sin ellos. La contención de verdad es
**dónde corre** (una VM que puedas tirar), **el alcance del token** y **la revisión del diff**. Se conserva el
**firewall de salida** porque no detiene a un agente hostil pero sí al fallo dominante: el despiste.

**Qué la reabriría.** Que el DinD rootless en devcontainers deje de tener el issue #479 abierto y aparezca una
receta verificada; ahí se recupera aislamiento sin perder Docker.

### D6 · Java 25 (LTS) y Spring Boot 4.1.x (2026-09-09)

Java **25 LTS**: ni bajar de 25 ni subir a 26. Boot **4.1.1** (el arquetipo venía en 4.0.2). El devcontainer
trae Temurin 25 precisamente para que el arquetipo se pruebe con el mismo JDK que usarán sus proyectos.

**Sobre subir a JDK 26** (se planteó al verlo disponible en Spring Initializr, 2026-09-09): **no**.

- **26 no es LTS.** GA el 17 de marzo de 2026, con seis meses de soporte que terminan al salir JDK 27 —
  o sea, ahora mismo. Adoptarlo sería estrenar un JDK que ya deja de recibir actualizaciones.
- **El siguiente LTS es Java 29** (septiembre de 2027). 26, 27 y 28 son todos de vida corta.
- Boot 4.1 lo *soporta* (hasta 26 inclusive), pero su **soporte de primera clase y el testing de
  native-image están sobre Java 25**.
- Que Initializr lo ofrezca no es señal de que convenga aquí: Initializr sirve proyectos individuales, que
  pueden saltar cada seis meses. Esto es la **base de muchos servicios**, y cada uno heredaría esa noria.

**Qué la reabriría.** La salida de **Java 29 LTS** (sept. 2027). Para experimentar con un no-LTS antes, que
sea en un servicio concreto cambiando `<java.version>`, no en el arquetipo.

### D7 · Seguridad: una sola cadena, siempre activa (2026-09-09)

**Decisión.** Una única `SecurityFilterChain` en el proyecto, **activa en todos los entornos y también en
los tests**. Stateless, con **OAuth2 Resource Server (JWT)** — nada de filtros JWT escritos a mano.

**La idea que lo ordena todo: lo que cambia entre entornos no son las reglas, es de dónde salen los
tokens.** En `dev` y `test`, un decodificador local con secreto HS256 (`configs/DevJwtConfig`) y
`make token` para acuñar uno; en el resto, el emisor de verdad vía `issuer-uri`. La cadena es idéntica en
los dos sitios.

**Por qué, y qué se descarta.** El patrón habitual —un perfil que apaga la seguridad para poder
trabajar— tiene un coste que no se ve hasta que duele: si en local no hay seguridad, **los fallos de
autorización se descubren en el primer despliegue**. Y si los tests la desactivan, no están probando la
aplicación que se despliega. Por eso aquí no hay perfil `nosecurity` ni `SecurityDisableForTestConfig`:
los tests se autentican como un cliente real, con `.with(jwt())`.

**CSRF desactivado, pero por el motivo correcto y escrito en el código:** la credencial viaja en la
cabecera `Authorization` y el navegador no la adjunta sola, así que no hay vector que proteger.
⚠️ Ese razonamiento **deja de valer** si algún día se añade autenticación por cookie de sesión,
`httpBasic` o mTLS. El snippet `httpBasic() + csrf.disable()` que circula por todas partes es inseguro.

**CORS explícito y cerrado por defecto**, porque casi todo el dolor de «no puedo llamar a la API desde el
front en local» es CORS y no autenticación, y el error que ve el navegador no lo dice.

**Verificado de verdad, contra la aplicación levantada**, no solo con tests: sin token 401; con el token
de `make token` 200/201; con el token manipulado 401; `/actuator/health` público pero `/actuator/env` 401.
Además `SeguridadActivaIT` deja eso mismo en el gate, **sin `@ActiveProfiles`**, para que nadie pueda
apagar la seguridad sin que algo se ponga rojo.

**Qué la reabriría.** Necesitar clientes de navegador con sesión por cookie: ahí CSRF vuelve.

---

## Tropiezos ya pagados

Numerados para poder citarlos. **No los redescubras ni los "arregles" otra vez.**

- **G1 · Los buscadores de artefactos devuelven versiones desfasadas.** Comprueba siempre en
  `https://repo1.maven.org/maven2/<ruta>/maven-metadata.xml`. Todas las versiones de este repo se fijaron así.
- **G2 · Boot 4 partió las autoconfiguraciones en starters.** `flyway-core` (o `liquibase-core`) a secas
  **no migra nada y no avisa**. Hace falta `spring-boot-starter-flyway`. Sospecha lo mismo de cualquier
  librería suelta que "debería" autoconfigurarse.
- **G3 · Flyway necesita el módulo del motor aparte**: `flyway-database-postgresql` además de `flyway-core`.
  Omitirlo es el error de arranque más común al migrar.
- **G4 · Testcontainers 2.0 renombró todos los módulos a `testcontainers-*`** y movió las clases de contenedor
  a `org.testcontainers.<modulo>`. Además quitó JUnit 4.
- **G5 · Ryuk no alcanza `172.17.0.1` desde dentro de un devcontainer** → todos los `*IT` mueren con
  `Could not connect to Ryuk`, y el error no apunta por ningún lado a la red. Se resuelve con un **perfil
  Maven activado por la existencia de `/.dockerenv`** (solo dentro de un contenedor; en CI, que corre sobre la
  VM, no se activa porque allí el comportamiento por defecto ya es correcto). Va **en el pom de la
  plantilla**: todo servicio generado se desarrolla en un devcontainer, así que se paga una vez aquí y no una
  vez por proyecto.
- **G6 · Un fichero de la plantilla que no esté declarado en `archetype-metadata.xml` NO llega al proyecto
  generado.** Es lo que más despista del repo. Ya nos costó: `k8s/` con los `fileSet` comentados (y
  `skaffold.yaml` generándose y apuntando a manifiestos inexistentes) y `liquibase.bash` declarado como
  `liquibase.sh`, que por la extensión no se copiaba nunca. **Regla: tras tocar la plantilla, comprueba en el
  proyecto generado que tu fichero está ahí.**
- **G7 · `BUILD SUCCESS` del arquetipo no significa nada.** Ese build solo empaqueta ficheros de texto, no los
  compila. El único verde que cuenta es `mvn compile` / `mvn verify` **sobre el proyecto generado**.
- **G8 · `${revision}` y multi-módulo se llevan mal con `-pl`**: construir un módulo suelto no resuelve la
  versión del padre si no está instalado. Se mitiga con `.mvn/maven.config` fijando un `-Drevision` por
  defecto, y construyendo desde la raíz con `-am` cuando acotes.
- **G9 · `application.properties` y `application.yaml` a la vez.** Spring carga los dos y **gana el
  `.properties`**. Tener los dos con contenido solapado y distinto hace que editar el `.yaml` no tenga
  efecto, sin ningún aviso. Se deja **uno solo**.
- **G10 · `spring.threads.virtual.enable` es un typo silencioso** (es `enabled`): los virtual threads no se
  activan y nada lo dice.
- **G11 · Propiedades que apuntan a dependencias que no están.** Había `spring.cache.type=redis` sin
  `spring-boot-starter-data-redis` en el pom. Si configuras algo, la dependencia va con ello.
- **G12 · `ddl-auto: update` convierte la herramienta de migraciones en adorno.** Con Flyway al mando,
  `ddl-auto` es **`validate`** — y ese `validate` es justamente el detector de deriva (ver D2).
- **G13 · El ejemplo devolvía la entidad JPA en el controlador** (con un `@OneToOne(LAZY)` a sí misma) en vez
  del DTO que ya existía. En una plantilla eso no es un descuido: es un antipatrón que se copia.
- **G14 · Hay dos `.devcontainer/` y no tienen relación.** El de la raíz es el de *este* repo; los de
  `archetype-resources/.devcontainer/{base,full}/` son la plantilla que se copia al proyecto generado. Tocar
  uno no cambia el otro.
- **G15 · Con Liquibase, `liquibase-hibernate6` contra Hibernate 7 no vale** (Boot 4.1.1 trae Hibernate
  7.4.5; existe `liquibase-hibernate7`). Queda anotado por si D2 se reabre alguna vez.
- **G16 · El Maven wrapper del proyecto generado estaba roto por partida triple**: se copiaban `mvnw` y
  `mvnw.cmd` pero **no** `.mvn/wrapper/maven-wrapper.properties` (sin el, el wrapper no sabe qué Maven
  bajar), el `.gitignore` generado ignoraba `/.mvn/` entero (con lo que tampoco se habría versionado), y
  encima `mvnw` salía sin permiso de ejecución. Arreglado: se genera `.mvn/` completo y el `.gitignore` ya
  no lo excluye.
- **G17 · Velocity se come los `##`.** Al marcar un fichero como `filtered="true"` pasa por Velocity, donde
  `##` es un **comentario de línea**: cualquier cabecera markdown `## Titulo` desaparece del fichero
  generado, en silencio. Se escapa con el patrón que ya usaba `readme.md`: `#[[##]]# Titulo`. Un `#` suelto
  seguido de espacio **no** es directiva y es seguro.
  ⚠️ **Y no era teórico: ya estaba pasando.** `MAKEFILE.md` llegaba al proyecto generado con **54 líneas
  menos y CERO cabeceras**, y `SKAFFOLD.md` con **76 menos**. Es decir, la documentación del proyecto
  generado llevaba mutilada desde siempre y nadie lo vio, porque el fichero *existe* y casi todo el texto
  sigue ahí. `readme.md` se salvaba por casualidad: era el único que ya venía escapado.
  **Comprobación barata:** `wc -l` de la plantilla contra el generado. Si no coinciden, Velocity se comió algo.
- **G18 · Los arquetipos NO conservan el bit de ejecución.** Todo sale 644, así que el primer `./mvnw` del
  proyecto recién generado responde `Permission denied` — justo en el comando que documenta el readme. Se
  resuelve con `src/main/resources/META-INF/archetype-post-generate.groovy`, que hace `+x` tras generar.
- **G20 · `devcontainer.json` es JSONC: admite comentarios, pero las claves VAN ENTRECOMILLADAS.** Los dos
  que enviábamos a los proyectos generados (`base/` y `full/`) tenían `name:`, `features:`, etc. **sin
  comillas**: no eran JSON válido y ese devcontainer **nunca llegó a abrir**. Nadie lo detectó porque el
  fichero existe, se ve bien y nadie lo abría. Comprobación barata, y merece estar en CI:
  `sed -E 's|^\s*//.*$||' devcontainer.json | jq empty`.
- **G21 · Hay DOS `.devcontainer/` en este repo y no tienen relación.** El de la raíz es el de trabajar
  *sobre* el arquetipo; el de `archetype-resources/.devcontainer/` es la plantilla que se copia a los
  proyectos generados. Tocar uno no cambia el otro. (Es el mismo tipo de despiste que G14, pero con los
  devcontainers.)
- **G22 · Postgres 18 cambió el punto de montaje del volumen.** Espera **un solo** montaje en
  `/var/lib/postgresql` (los datos van en un subdirectorio), no en `/var/lib/postgresql/data` como antes.
  Con el punto antiguo el contenedor arranca, falla y muere — y el síntoma que ves es **la aplicación
  quejándose de que no encuentra `DataSource`**, que no apunta a esto por ningún lado. Lo tuvimos roto y
  no se veía porque los tests usan Testcontainers, que no monta volumen: `make verify` estaba en verde y
  `make run` no arrancaba.
- **G23 · `spring.docker.compose.file` es una ruta RELATIVA al directorio de trabajo.** Al pasar a
  multi-módulo, la app se arranca con `mvn -pl app`, el proceso corre desde `app/` y dejaba de encontrar
  el `compose-app.yml` de la raíz. Mismo síntoma engañoso que G22. Se resolvió declarando la conexión
  explícitamente en el perfil `dev` y **desactivando** el soporte de compose: `make docker-up` ya
  levantaba los servicios, así que había dos mecanismos para lo mismo.
- **G24 · `@WithMockUser` NO autentica con `SessionCreationPolicy.STATELESS`.** No hay repositorio de
  contexto de seguridad donde dejar la autenticación que prepara la anotación, así que la petición llega
  sin credenciales y responde **401**. En los tests de una API stateless se usa `.with(jwt())`, que
  además es lo fiel: es como se autentica un cliente de verdad. `@WithMockUser` sigue valiendo en los
  slices `@WebMvcTest`, que usan la seguridad por defecto.
- **G25 · Velocity también parsea los COMENTARIOS.** Una referencia con valor por defecto (dólar, llave,
  variable, dos puntos, defecto) escrita dentro de un comentario **rompe la generación entera**. Nos pasó
  escribiendo el comentario que explicaba justamente cómo escaparlas. Se escapan con `#[[...]]#`, estén
  donde estén.
- **G19 · `${spring-boot.version}` no se resuelve en el pom de un módulo hijo.** Al pasar a multi-módulo, el
  `<parent>` de `app` ya no es `spring-boot-starter-parent` sino el padre del proyecto, así que
  `${project.parent.version}` pasó a valer `${revision}`. Cuidado al mover bloques entre poms: las
  referencias relativas al padre cambian de significado sin avisar.

---

## Lo siguiente

- [x] **Dejar verde lo generado.** ✅ 2026-09-09. Eran tres cosas: `@InheritConfiguration` sin importar, la
      propiedad `myTableParent` sin mapear (el campo de la entidad empezaba por mayúscula) y cinco ficheros
      de test con el prefijo `import $` perdido en el fuente. Verificado: `mvn test` → 12 tests en verde.
- [x] **Multi-módulo (D1) + flatten (D3).** ✅ 2026-09-09. Hechos a la vez porque se tocan (G8). Verificado
      punto por punto, no por inspección:
      · el reactor construye los 3 módulos y pasa los 12 tests;
      · `-Drevision=1.2.3` en la CLI **gana** a `.mvn/maven.config`;
      · el **pom instalado lleva `1.2.3` resuelto**, sin el literal `${revision}` (el bug de D3, cerrado);
      · `mvn -pl app` construye el módulo suelto (G8 mitigado);
      · `./mvnw` arranca y descarga Maven 3.9.16 (G16 + G18).
      De paso se generan ya los `k8s/` (antes `skaffold.yaml` se generaba apuntando a manifiestos
      inexistentes) y se borraron `__gitignore` y `old__Dockerfile`, que eran duplicado y código muerto.
- [x] **Flyway** (D2) con el flujo de [migraciones.md](migraciones.md). ✅ 2026-09-09. Fuera Liquibase
      entero (dependencia, plugin, `liquibase.properties`, `db/changelog/`, `liquibase.bash` y su
      documentación); dentro `spring-boot-starter-flyway` + `flyway-database-postgresql` (G2, G3).
      **El flujo de bootstrap se probó de verdad, no se documentó y ya**: `make db-baseline` arranca el
      perfil `ddl-dump`, vuelca el DDL desde el metadato de las entidades **sin base de datos ni Docker**, y
      el `V1__init.sql` resultante —Envers incluido (`my_table_aud`, `revinfo`)— se revisó a mano y viaja en
      la plantilla. Así el proyecto generado nace con esquema y `validate` funcionando.
      De paso se arreglaron en la entidad de ejemplo las tres cosas que el DDL destapó: la columna
      `my_tableparent`, los `varchar(255)` que contradecían el `@Size(max = 200)` del DTO, y el nombre de
      constraint generado por Hibernate.
- [x] **Unificar la configuración** (G9). ✅ 2026-09-09. Un solo `application.yaml` por fuente. De paso:
      `spring.threads.virtual.enabled` (era `enable`, G10), fuera `spring.main.lazy-initialization`, y el
      starter de Redis que faltaba aunque la configuración ya declaraba `spring.cache.type=redis` (G11).
- [x] **Demostrar el oráculo del esquema.** ✅ 2026-09-09, tras darle Docker al devcontainer (D5).
      `make verify` sobre el proyecto generado: **12 tests unitarios + 12 de integración en verde**, con
      evidencia en el log de que el ciclo completo funciona y no solo compila:
      · `Migrating schema "public" to version "1 - init"` → Flyway aplica el `V1__init.sql` desde vacío;
      · `Successfully applied 1 migration`, y los contextos siguientes ven `Current version: 1`;
      · **cero errores de validación** con `ddl-auto: validate` → las entidades cuadran con el esquema que
        construyó Flyway. Eso es exactamente el detector de deriva de D2, funcionando.
      · Ryuk arrancó sin el `Could not connect` de G5 y no dejó contenedores huérfanos.
      Hasta aquí, todo lo que se decidió está ejercitado, no solo escrito.
- [ ] **Modernizar versiones**: Boot 4.0.2 → 4.1.1 y el resto de la tabla del onboarding. Incluye subir el
      Maven wrapper (hoy 3.2.0) y los plugins del arquetipo (archetype 3.2.1 → 3.4.1, gpg, central-publishing).
- [x] **CI y el gate.** ✅ 2026-09-09. `make verify` es ahora EL GATE (clean → build → generate → check →
      `mvn verify` del generado) y `.github/workflows/verify.yml` corre **exactamente ese comando** en cada
      push a `main`, cada PR, a mano y semanalmente. Que sea el mismo comando no es estética: si el gate
      local y el CI divergieran, el CI dejaría de significar nada.
      Además `scripts/check-generated.sh` convierte seis gotchas en comprobaciones automáticas (C1..C6, ver
      [flujo-de-trabajo.md](flujo-de-trabajo.md)), **todas verificadas rompiendo el proyecto a propósito**:
      una comprobación que nunca falla no vale nada.
      De paso murió `restart_build.bash` (duplicado literal del Makefile) y el `MAKEFILE.md` de la raíz
      (509 líneas documentando targets que ya no existían).
- [ ] **Decidir sobre `k8s/` y `skaffold`.** Ya no están rotos (se generan), pero **nunca se han usado**.
      La pregunta abierta no es cómo mejorarlos, es **quitarlos o hacerlos reales**: andamio que nadie
      ejercita se pudre. Skaffold se usa en otros proyectos con minikube; hay que mirar si sigue siendo la
      opción recomendable hoy antes de decidir.
- [ ] **Parent POM publicado** (D4), ya en segunda fase.
