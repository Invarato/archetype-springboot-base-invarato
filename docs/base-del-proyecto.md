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
├── contract/                  <artifactId>-contract · el openapi.json, versionado y empaquetado
├── client-java/               cliente Java generado del contrato
├── client-python/             cliente Python generado del contrato
├── helm/<artifactId>/         chart de despliegue, con values por entorno
├── compose.yaml · Dockerfile · .devcontainer/
```

**Por qué.** La necesidad real y repetida son **clientes Java y Python, stubs y el `openapi.json`**, que hoy
no tienen dónde vivir. Y el coste está muy mal repartido en el tiempo: montarlo al generar cuesta un pom y un
nivel de carpeta; hacerlo después es un refactor que toca `Dockerfile`, `compose`, el despliegue y CI a
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
para sus tests de integración (Testcontainers) y para su `compose.yaml`; un entorno donde solo se puede
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

### D8 · El contrato es code-first, y los clientes se generan de él (2026-09-09)

**Decisión.** El reactor es `app` → `contract` → `client-java` → `client-python`, en ese orden y por ese
motivo: **`app` PRODUCE el contrato**, los demás lo consumen.

- **Code-first**: la fuente de verdad son los controladores; springdoc deriva el OpenAPI. Encaja con el
  objetivo del arquetipo (desarrollar rápido: escribes Java y el contrato cae solo). Contract-first —
  escribir el `openapi.json` a mano y generar las interfaces del servidor— es más rigurosa y más lenta.
- **El contrato se genera desde un TEST**, no con un plugin de Maven: el plugin tendría que arrancar la
  aplicación en una fase aparte, con su base de datos; el test reaprovecha el contexto que ya se levanta
  con Testcontainers. Y de paso hace de guardián: si el contrato cambia sin actualizar el fichero
  versionado, el build se pone rojo. `make openapi` acepta el cambio.
- **Los clientes se generan, no se comparten los DTOs del servidor.** Si un consumidor dependiera del jar
  de `app`, arrastraría Spring Boot, JPA y las entidades, y quedaría atado a la misma versión de Boot y de
  Java. Generando desde el contrato, el único acuerdo es el contrato.

**Qué la reabriría.** Que varios equipos negocien la API antes de implementarla: ahí contract-first gana.

### D9 · OpenRewrite, apuntando al problema de fondo de D4 (2026-09-09)

Se añade `rewrite-maven-plugin` al pom padre, **sin `executions`**: no corre en el build, se lanza a mano
(`./mvnw rewrite:run`, o `rewrite:dryRun` para ver qué cambiaría).

**Por qué está aquí.** Subir de versión mayor de Spring Boot son cientos de cambios mecánicos (paquetes
movidos, APIs renombradas). Hacerlos a mano en cada servicio es donde se pierden las tardes. Pero lo
importante es lo otro: es el **primer paso hacia la vía de actualización que a los arquetipos les falta**
(D4). Un arquetipo genera y se olvida; una receta se puede ejecutar sobre un proyecto ya generado.

---

### D10 · Despliegue con Helm, y fuera skaffold (2026-09-09)

**Skaffold: eliminado.** No por obsoleto —sigue mantenido (v2.22.0, julio de 2026)— sino por el criterio
nº2 de este documento: se enviaban **937 lineas de documentacion y 6 perfiles** para una herramienta que
**nunca se habia usado**, y cuyos perfiles `staging` y `prod` apuntaban a un chart de Helm que no existia.
Su valor real es iterar *contra un cluster*; el bucle de este arquetipo es `make run` con compose.
Volver a anadirlo el dia que haga falta son unas 50 lineas de YAML.

**`k8s/` con kustomize → chart de Helm con values por entorno.** El chart parametriza de verdad
(`values-dev.yaml`, `values-prod.yaml`) en vez de parchear YAML por capas.

**Y se quitan los despliegues de Postgres y Redis**, que eran una trampa: el ejemplo desplegaba Postgres
como `Deployment` con un `PersistentVolumeClaim`. Eso es perdida de datos esperando a pasar —no tolera
escalado ni actualizaciones rolling— y como *ejemplo a copiar* es peligroso. En un cluster real va un
servicio gestionado o un operador; el local ya lo cubre `compose.yaml`.

**Verificado con Helm de verdad** (`helm lint` + `helm template` en un contenedor efimero), no por
inspeccion. Es lo que destapo G29.

**Que la reabriria.** Necesitar iterar contra un cluster a diario: ahi skaffold, Tilt o DevSpace vuelven a
tener sentido.

> **Reabierta a medias el 2026-09-18.** Hacia falta ese bucle, asi que existe como
> [receta](recetas/kubernetes-local.md) —kind + Skaffold desplegando el chart que ya hay—, verificada
> contra un cluster de verdad dentro del devcontainer. **Sigue fuera del arquetipo por defecto**: la
> configuracion de Skaffold es una eleccion de equipo, y el bucle normal (`make run`) es mas rapido que
> cualquier cluster. La decision original aguanta; lo que cambia es que ahora el camino esta escrito.
>
> Y montar la receta destapo dos fallos del chart que llevaban ahi desde el principio (G50, G51): los
> nombres de objeto no los valida `helm lint`, los valida el API server.

### D11 · Cobertura, javadoc como contrato, y la API contra su propio contrato (2026-09-10)

**JaCoCo, sin umbral que rompa el build.** La cobertura sirve para **ver qué no está probado**, no como
nota que aprobar. Un mínimo puesto a ojo se cumple escribiendo tests que ejecutan código sin comprobar
nada: el número sube, la confianza no, y encima estorba. Si algún día se pone umbral, que salga de la
cobertura real medida, no de un número redondo.

**El javadoc pasa a ser la documentación de la API** (`therapi-runtime-javadoc`). Mantener una
documentación ya cuesta; mantener dos que dicen lo mismo es como se desincronizan.

⚠️ **Y tiene una consecuencia que hay que entender antes de escribir una línea:** al activarlo, **el
javadoc deja de ser interno**. El de una clase acaba siendo la descripción del tag en el contrato; el de
un método, la de su operación; el de un componente de un record, la de esa propiedad. Se comprobó en
carne propia: la primera generación publicó en el contrato un aviso interno que decía «⚠️ de aquí solo
salen DTOs… ArchitectureTest lo vigila», visible para todos los consumidores de la API.

**La regla que queda:** *javadoc = lo que un consumidor necesita saber. Comentarios `//` = lo que
necesita saber quien mantiene el código.* Verificado de extremo a extremo: un javadoc acaba en el
contrato, en el cliente Java, en el cliente Python y en su documentación markdown. Una fuente, cuatro
destinos.

**Schemathesis contrasta la API con su contrato.** Genera casos de prueba **desde el esquema** —tipos,
formatos, límites, enums— y comprueba propiedades que deben cumplirse siempre: que no haya 500, que la
respuesta case con lo documentado, que la API no viole su propio contrato. Aporta lo que los tests no
pueden: los tests prueban los casos que se nos ocurrieron; esto explora los que **no**.

⚠️ **Complementa, no sustituye.** Demuestra que la API no se rompe ni miente; **no** que la lógica de
negocio sea correcta. Para eso siguen estando los tests de siempre.

**Y va en CI, no solo en un `make`.** Un gate que hay que acordarse de lanzar no es un gate — la misma
lección que ya nos costó con `make verify`. Corre en su **propio job**, para que un fallo diga «la API ya
no cumple lo que promete» en vez de perderse entre los tests normales. Con cadencia de dos niveles: pocos
ejemplos en cada push para no alargar los PR, búsqueda profunda en la ejecución semanal.

⚠️ Detalle que lo haría inútil si se pasa por alto: la API exige autenticación, así que hay que pasarle un
token. Sin él, Schemathesis solo recibiría 401 en todo y **pasaría en verde sin haber probado nada**.

**El proyecto generado ya nace con CI.** Era un hueco de fondo que este trabajo destapó: el arquetipo
tenía CI para sí mismo y generaba proyectos sin ninguna.

**Qué la reabriría.** Que el fuzzing dé demasiados falsos positivos y se acabe desactivando: ahí lo
correcto sería acotar `--checks`, no convivir con él en rojo.

### D12 · Qué entra en el arquetipo: la regla del test (2026-09-10)

La pregunta se planteó como «¿metemos Redis, Kafka, WebFlux, WebSocket, gRPC como ejemplos usables, o
sobrecargamos?». La respuesta salió de mirar el propio repo, porque **el experimento ya estaba hecho**:
`spring-boot-starter-webflux` y `spring-boot-starter-data-redis` llevaban tiempo en el pom con **cero
usos** en el código. Nadie los había quitado. Y Redis no salía gratis: un contenedor arrancaba **cinco
veces por suite** sin que ninguna prueba lo usara, y con una versión distinta (`redis:7`) de la que se
despliega (`redis:8`).

Ese es el argumento contra el «me lo descargo y quito lo que no use»: **una dependencia sin ejemplo es
invisible**. No molesta, no falla, no se ve. Solo pesa, amplía la superficie de ataque y hay que
actualizarla.

**La regla:** *lo que entra en el arquetipo lo ejercita un test de la puerta.* Sin test, no entra. Decide
los casos futuros sin volver a discutirlos, y decidió estos:

| | Qué se hizo | Por qué |
|---|---|---|
| **WebFlux** | **Fuera** | Cero usos. Además, mezclar `starter-webflux` con `starter-web` en una app de servlets es fuente de comportamientos raros. Para llamar a otros servicios, `RestClient` ya viene en `starter-web`. |
| **Redis / caché** | **Se hace real** | Se pagaba el 100% del coste (dependencia, contenedor, configuración, compose) por el 0% del valor. Ahora hay `@Cacheable`/`@CacheEvict` de ejemplo y un `CacheIT` que prueba que guarda **y** que invalida. |
| **Kafka, WebSocket, gRPC** | **[Recetas](recetas/readme.md), no código** | No son «una dependencia más»: cambian la forma de la aplicación. Kafka mete un broker en la puerta; gRPC, un segundo puerto y una cadena de protos; WebSocket rompe las dos historias sobre las que está construido esto (stateless y contrato OpenAPI). Un arquetipo con las tres dentro deja de ser una base y pasa a ser una demo. |

**Varios arquetipos, no.** Multiplicaría por N el mantenimiento de la puerta, que es la parte cara y la
que da el valor.

**Las recetas no son un folleto.** Están en [docs/recetas/](recetas/readme.md), y cada una **se aplicó a
un proyecto generado antes de escribirla**: los pasos son los que funcionaron, los tropiezos son los que
aparecieron al hacerlo y las cifras están medidas. De ahí salieron tres hallazgos que no se veían desde
fuera: los listeners de Kafka metían 14 errores de conexión en tests que no lo usan; el handshake de
WebSocket da 401 porque **es una petición HTTP** y pasa por la cadena de seguridad; y el servicio gRPC
nace igualmente protegido, con el token en la metadata de la llamada.

**Qué reabriría esto:** que una integración concreta se repita en la mayoría de proyectos generados. Si
está en todos, deja de ser opcional y entra — con su test.

### D13 · Logging estructurado nativo, sin dependencia externa (2026-09-10)

El JSON de los logs lo hacía `net.logstash.logback:logstash-logback-encoder`, con 30 líneas de XML y una
versión que había que revisar a mano porque el BOM no la gestiona.

Desde Spring Boot 3.4 el logging estructurado viene de serie, y desde la 3.5 cubre lo único que faltaba
para poder cambiar: el recorte de trazas con la causa raíz primero
(`logging.structured.json.stacktrace.root: first`). Se comprobó arrancando la aplicación con el perfil
`prod` y provocando un fallo real: la traza empieza por `ConnectException` —la causa de verdad— y luego
lista los envoltorios, con el recorte a 2048 caracteres exacto.

Se gana además `service.version` en cada línea, que el encoder anterior no ponía.

**Por qué se cambió y no «porque es más nuevo»:** una dependencia menos de las pocas cuya versión no
gobierna el BOM, y 30 líneas de XML que pasan a ser configuración por entorno. Si el formato nativo se
quedara corto para algún recolector, el encoder externo sigue siendo una opción legítima.

### D14 · Imagen: jar ya construido y por capas · puerto fijo pero sobreescribible (2026-09-18)

**El Dockerfile ya no compila dentro de la imagen.** Antes sí, con Maven, y para aprovechar la caché de
capas había que copiar los `pom.xml` **módulo a módulo** — o sea, repetir la estructura del proyecto
dentro del Dockerfile. Cuando el proyecto pasó de dos módulos a cuatro, esa lista se quedó en dos y la
imagen dejó de construirse (G45). Ahora parte del jar que produce `mvn package`, así que no hay nada que
repetir. De paso, `docker build` baja a **4 segundos**.

**Y se parte en capas** (`-Djarmode=tools ... extract --layers`). Un jar de Spring Boot son ~60 MB de los
que tu código es una fracción mínima; copiado entero, cambiar una línea invalida la capa completa y el
push se lleva los 60 MB otra vez. Con cuatro capas ordenadas por frecuencia de cambio, solo se mueve la
última.

**¿Y por qué no *buildpacks* (`spring-boot:build-image`), que no necesitan Dockerfile?** Porque generan
una imagen que no eliges: base Paketo, opaca y difícil de justificar donde exigen una imagen endurecida
concreta o un registro interno. Un arquetipo es una **base para adaptar**, y un Dockerfile de 40 líneas
que se lee entero se adapta; un builder no. Quien no tenga esa restricción puede borrar el Dockerfile y
usar `./mvnw spring-boot:build-image` sin tocar nada más — por eso no se cierra la puerta.

**El puerto: fijo por defecto, sobreescribible con `SERVER_PORT`.** Las dos mitades importan, y confundir
los dos escenarios es lo que lleva a elegir mal:

- **En el contenedor y en Kubernetes no hay colisión**: cada pod tiene su IP. Un puerto aleatorio ahí no
  simplifica nada y rompe las probes del chart, el `EXPOSE`, el compose y los objetivos del Makefile.
- **La colisión aparece en tu máquina**, al levantar varios microservicios a la vez. Se resuelve sin
  tocar ficheros: `SERVER_PORT=8081 make run`.

`SERVER_PORT=0` (que lo elija Spring) también funciona, pero el peaje es que después no sabes a qué
puerto llamar: ni `make actuator-health`, ni el enlace de la documentación, ni el reenvío de puertos del
devcontainer. Para varios servicios sale más a cuenta asignar a cada uno el suyo que dejarlos al azar.

**Y la imagen se construye en la puerta** (`make check-image`). Era el agujero que permitió que estuviera
rota semanas: `mvn verify` no construye imágenes, así que nadie se enteraba.

**Qué reabriría esto:** una política de empresa que imponga imágenes producidas por buildpacks o por una
cadena de build propia.

### D15 · El compose de desarrollo lo levanta Spring Boot (2026-09-18)

`make run` ya no necesita `make docker-up` delante: en el perfil `dev`, Spring Boot levanta
`compose.yaml`, **espera a que los servicios estén sanos** y deduce de ahí las conexiones. Arrancar es
una orden, o el botón del IDE.

Estaba desactivado por dos razones, y las dos eran ciertas pero no definitivas:

- *«Dos mecanismos haciendo lo mismo»*. Cierto, y por eso ahora hay uno: `run` ya no depende de
  `docker-up`, que queda para levantar los servicios sin la aplicación.
- *«`spring.docker.compose.file` es relativo al directorio de trabajo y `mvn -pl app` corre desde
  `app/`»*. Cierto, y **tiene arreglo**: `workingDirectory` en el `spring-boot-maven-plugin` apuntando a
  la raíz del reactor.

**Lo que se gana no es sólo comodidad.** El perfil `dev` repetía usuario, contraseña y puertos que ya
declara `compose.yaml`: dos sitios con el mismo dato, y el día que se separan el error es
«password authentication failed», que no señala a nadie. Ahora esos datos están en un solo sitio.

**Lo que costó:** arreglar la activación del perfil de Maven destapó dos problemas latentes (G48, G49).
Nada de esto era visible antes, porque el perfil llevaba meses sin activarse.

**Qué reabriría esto:** trabajar habitualmente contra servicios que no están en el compose —una base de
datos compartida del equipo, por ejemplo—. Se apaga con `SPRING_DOCKER_COMPOSE_ENABLED=false`.

---

## Auditoría contra prácticas obsoletas (2026-09-10)

Revisión punto por punto contra una lista pública de patrones de Spring Boot ya desaconsejados. Se hizo
**buscando en el código**, no de memoria; tres de los resultados eran falsos positivos que solo aparecían
en comentarios que explican por qué NO se usa eso.

| Práctica desaconsejada | Estado |
|---|---|
| Inyección por campo con `@Autowired` | ✅ No se usa; además **ArchUnit la prohíbe** en el build |
| `@Value` para configuración grande | ✅ `@ConfigurationProperties` + `@ConfigurationPropertiesScan` |
| `@Transactional` mal usado | ✅ `readOnly` en las lecturas, límites en la capa de servicio |
| `System.out.println` | ✅ Ninguno; SLF4J parametrizado |
| `WebSecurityConfigurerAdapter`, `antMatchers` | ✅ Un `SecurityFilterChain`, `requestMatchers` |
| `RestTemplate` | ✅ `RestClient` (el cliente generado ya lo usaba) |
| `WebMvcConfigurerAdapter`, `@EnableWebMvc` de más | ✅ Ninguno |
| `javax.*` | ✅ Todo `jakarta.*` |
| JUnit 4 (`@RunWith`) | ✅ JUnit 5 |
| `spring.factories` | ✅ No hay starter propio todavía ([D4](#d4--el-parent-pom-publicado-va-en-una-segunda-fase-2026-09-09)) |
| Errores como mapas sueltos | ✅ `ProblemDetail`, y con **RFC 9457**, que sustituye a la 7807 |
| Hilos virtuales | ✅ `spring.threads.virtual.enabled: true` |
| **N+1 de JPA** | ❌ **Nada.** Corregido: ver G42 |
| **`open-in-view`** | ❌ **Sin configurar**, y Spring avisaba en cada arranque. Corregido: ver G40 |

Los dos huecos eran el mismo problema por dos sitios, y salió un tercero de propina (G41).

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
- **G31 · Renombrar un metodo de un controlador ES UN CAMBIO DE CONTRATO.** El nombre del metodo se
  convierte en el `operationId` del OpenAPI, y de ahi salen los nombres de los metodos en los clientes
  generados. Una limpieza de nombres que parece interna rompe a todos los consumidores. Lo caza el test
  de contrato, que para eso esta.
- **G30 · Tracing: dos trampas silenciosas.** (1) Spring Boot 4 movio la propiedad —era
  `management.otlp.tracing.endpoint`, ahora `management.opentelemetry.tracing.export.otlp.endpoint`— y
  con el nombre viejo **no falla nada**: arranca, el traceId sigue en logs y cabecera, y simplemente NO
  SE EXPORTA. (2) Una cadena **vacia no significa desactivado**: el exportador la valida y tumba el
  arranque con «Invalid endpoint». Lo que se apaga es `management.tracing.export.enabled`.
- **G32 · `@DataJpaTest` no carga las `@Configuration` del proyecto.** La auditoria de JPA no se aplicaba
  y los tests fallaban con «null value in column created_at» — un error que apunta a la base de datos
  cuando lo que falta es un `@Import`.
- **G33 · Dos claves iguales en el mismo documento YAML.** `yq` lo tolera (gana la ultima), pero Spring
  lo rechaza con «while constructing a mapping», que no menciona cual es la clave duplicada.
- **G34 · `@EnableSpringDataWebSupport(VIA_DTO)` arregla el JSON, pero NO el contrato.** La anotacion
  cambia lo que se serializa; springdoc, en cambio, documenta el **tipo declarado** en la firma del
  metodo. Devolviendo `Page<T>` con la anotacion puesta, el servidor manda `{content, page}` mientras el
  contrato sigue describiendo los 11 campos internos de `Page` — y los clientes generados a partir de el
  arrastran modelos (`PageableObject`, `SortObject`) de una respuesta que ya nadie envia. Contrato y
  realidad divergen **sin que falle nada**. La unica forma de que coincidan es declarar
  `PagedModel<T>` como tipo de retorno; la anotacion se queda como red de seguridad para el proximo
  `Page` que alguien devuelva sin pensarlo. Aviso general: en un proyecto *code-first*, lo que se
  publica sale de la **firma**, no del comportamiento.
- **G35 · La caché necesita DOS starters, y sin uno de ellos no cachea en silencio.**
  `spring-boot-starter-cache` trae la abstraccion (`@Cacheable`, y quien lee `spring.cache.*`);
  `spring-boot-starter-data-redis` trae la implementacion. Faltaba el primero, asi que habia un bloque
  `spring.cache.redis.*` detallado —tiempo de vida, prefijo, estadisticas— **que no leia nadie**. La
  aplicacion arranca igual y responde igual: lo unico que cambia es que no cachea. Corolario: un
  `@Cacheable` sin `@EnableCaching` tampoco da error, tambien se queda en nada.
- **G36 · Cachear valores en Redis: JSON con lista blanca, nunca «unsafe».** Con el serializador por
  defecto (serializacion de Java) cachear un `record` falla porque no es `Serializable`. Al pasar a JSON
  aparece el segundo golpe: sin informacion de tipo, lo que vuelve de Redis es un `LinkedHashMap` y el
  cast revienta con «LinkedHashMap cannot be cast to ...». La solucion es `enableDefaultTyping(...)` con
  un `PolymorphicTypeValidator` restringido a los paquetes propios. Existe `enableUnsafeDefaultTyping()`,
  de una linea, y el nombre no es decorativo: aceptar cualquier tipo convierte a quien pueda escribir en
  Redis en alguien que ejecuta codigo en el proceso.
- **G37 · Un test que escribe sin transaccion contamina a los demas.** `CacheIT` no puede heredar del
  base transaccional (necesita commits de verdad para distinguir «lo leyo de Redis» de «lo leyo de la
  base»), y las filas que dejaba sobrevivian: los tests del controlador empezaron a ver tres registros
  donde esperaban dos. Quien renuncia al rollback limpia el mismo, **antes y despues**.
- **G38 · Cuidado con los tests que pasan por un efecto colateral de otro.** El contenedor de Redis que
  arrancaba la clase base dejaba `/actuator/health` en UP «gratis». Al quitarlo —no lo usaba nadie— el
  test de que las sondas son publicas empezo a dar 503: llevaba tiempo pasando por un motivo que no tenia
  nada que ver con lo que comprobaba. En el perfil `test` se apaga `management.health.redis.enabled`; en
  produccion sigue encendida, que ahi si se quiere.
- **G39 · Afirmar sobre el estado interno en vez de sobre el comportamiento.** La primera version del
  test de borrado miraba dentro de la cache (`cacheManager.getCache(...).get(id)`) y resulto fragil.
  Comprobar el comportamiento —pedir el registro borrado da 404— es mas estable **y demuestra mas**: si
  la entrada siguiera cacheada, `findById` la devolveria en vez de lanzar, asi que esa asercion cubre a
  la otra. Regla: afirmar por la superficie publica, no por las tripas.
- **G40 · `open-in-view` estaba sin configurar, y Spring lo avisaba en CADA arranque.** El aviso
  («spring.jpa.open-in-view is enabled by default...») aparecia tres veces en el log del gate y llevaba
  ahi desde el principio: un log que nadie lee es un log que no existe. Lo grave no es el aviso sino lo
  que tapa: con OSIV la sesion de JPA sigue abierta al serializar la respuesta, asi que las relaciones
  perezosas se cargan solas, tarde y fuera de la transaccion — y **las consultas N+1 se vuelven
  invisibles**, porque ocurren lejos del repositorio y no fallan. Ahora va a `false`.
- **G41 · `show-sql: true` estaba activo en TODOS los perfiles, produccion incluida.** Vivia en el primer
  documento del yaml, no en el de `dev`. Con trafico real son miles de sentencias formateadas y escritas
  por minuto; y como `format_sql` las parte en varias lineas, ademas **rompe el log estructurado**: una
  entrada JSON por linea deja de ser una entrada por evento. Movido a `dev`.
- **G42 · El N+1, medido en vez de supuesto.** Cargando 5 registros y tocando su relacion: **1 consulta
  para la lista y 5 mas** para las relaciones. Con `hibernate.default_batch_fetch_size: 50` pasa a **2 en
  total**. Lo vigila `NPlusUnoIT`, que no comprueba el resultado sino **cuantas sentencias** se
  ejecutaron — la unica forma de que un N+1 se ponga rojo, porque nunca falla: solo va lento, y con pocos
  datos ni eso.
- **G43 · Una comprobacion que no se ejecuta, y un contador escrito a mano que lo tapaba.** C8 necesita
  el proyecto ya construido, pero `make verify` ejecutaba `check` ANTES del build: su guarda
  `if [ -d target/... ]` la saltaba **siempre**, en silencio. Y el resumen final decia «10 comprobaciones
  OK» porque el numero era un literal en el `printf`. Dos fallos que se protegian entre si: la
  comprobacion no corria y el contador juraba que si. Arreglado con dos fases (`check` y `check-post`) y
  contando las que pasan de verdad. La leccion no es nueva —«una comprobacion que nunca falla no vale
  nada»— pero esta vez se la aplico el propio script.
- **G44 · Un `${VAR:defecto}` sin escapar rompe la generacion entera, y menos mal.** Se comprobo
  metiendo uno a proposito: Velocity aborta y dice exactamente donde
  (`application.yaml: Encountered ":valor-por-defecto}" at line 333, column 30`). Por eso NO hace falta
  una comprobacion propia: el fallo es ruidoso y preciso, y llega antes de que exista nada que
  comprobar. Lo que si hay que recordar es la forma correcta: `#[[${VAR:defecto}]]#`. Un `${sin.dos.puntos}`
  pasa tal cual sin escapar, porque Velocity deja las referencias que no conoce.
- **G45 · El Dockerfile llevaba semanas roto y nadie podia saberlo.** Compilaba dentro de la imagen y,
  para cachear dependencias, copiaba los `pom.xml` uno a uno: `contract` y `app`. Al pasar el proyecto a
  cuatro modulos, `docker build` empezo a fallar con «Child module /workspace/client-java does not
  exist». **La puerta no lo veia porque `mvn verify` no construye imagenes.** Dos lecciones: un fichero
  que REPITE la estructura del proyecto se desincroniza (la solucion no fue actualizar la lista, fue
  eliminarla), y todo lo que el arquetipo promete tiene que ejecutarse en la puerta — ahora hay
  `make check-image`.
- **G46 · Docker exige el nombre de imagen en MINUSCULAS.** Con un `artifactId` en camelCase,
  `make docker-build` del proyecto generado fallaba con «repository name must be lowercase», y ya esta:
  ninguna pista de que el problema era el nombre del proyecto. Es el mismo fallo que G29 con Helm y RFC
  1123, en otra herramienta: **lo que tu llamas al proyecto acaba siendo el nombre de objetos que tienen
  sus propias reglas**. Ahora el Makefile lo pasa a minusculas.
- **G47 · Un HEALTHCHECK que no usaba nadie y metia red en el build.** Para poder hacerlo con `curl`
  habia un `apt-get install` en el Dockerfile: una capa mas, mas tamaño y —lo caro— una **dependencia de
  red en tiempo de build**, que es de las cosas que rompen un dia sin avisar (de hecho rompio). Y el
  consumidor no existia: **Kubernetes ignora el HEALTHCHECK de Docker** —usa las probes del Deployment,
  que estan en el chart— y el compose de este proyecto levanta Postgres y Redis, no la aplicacion. La
  imagen base (JRE sobre Ubuntu) no trae curl ni wget ni nc. Fuera, con la receta de tres lineas en un
  comentario por si alguien lo necesita.
- **G48 · `activeByDefault` se apaga en cuanto se activa CUALQUIER otro perfil del mismo pom.** El perfil
  `dev` —el que aporta devtools y el soporte de Docker Compose— usaba `activeByDefault`. Justo debajo
  habia otro, `devcontainer`, que se activa solo con que exista `/.dockerenv`. Resultado: **dentro del
  devcontainer, que es el entorno que este proyecto recomienda, el perfil `dev` no se activaba nunca**.
  No fallaba nada: simplemente no habia recarga en caliente ni contenedores automaticos, y no habia por
  donde sospecharlo. Se ve con `mvn help:active-profiles`, que es lo que conviene mirar antes de creerse
  que un perfil esta activo. La solucion es activar por propiedad negada
  (`<property><name>!sinHerramientasDeDesarrollo</name></property>`), que se evalua por su cuenta.
- **G49 · DevTools en el classpath de los tests rompe la cache, y la propiedad para apagarlo NO basta.**
  Al arreglar G48, devtools llego por fin al classpath... y los tests de invalidacion de `CacheIT`
  empezaron a fallar: el valor viejo seguia ahi despues de un `@CacheEvict`. Es el classloader de
  reinicio, que carga las clases de la aplicacion aparte. Lo traicionero: poner
  `spring.devtools.restart.enabled: false` en el yaml **parece** el arreglo y no lo es — la propia
  documentacion de Spring Boot dice que esa propiedad sigue inicializando el classloader de reinicio.
  Paso: con ella, `CacheIT` en solitario pasaba y en la suite completa fallaba. La solucion es sacarlo
  del classpath con `classpathDependencyExcludes` en surefire y failsafe.
- **G50 · `helm lint` y `helm template` NO validan los nombres de objeto.** El chart llevaba
  `existingSecret: ${artifactId}-secret` e `image.repository: ${artifactId}`, o sea el nombre del
  proyecto en crudo. Con un artifactId en camelCase, el despliegue lo rechaza el API server: «a
  lowercase RFC 1123 subdomain must consist of lower case alphanumeric characters». G29 se dio por
  cerrado con `helm lint` + `helm template` y **no bastaba**: esas dos ordenes comprueban que el YAML se
  renderiza, no que Kubernetes lo acepte. Solo aparecio desplegando contra un cluster de verdad. Ahora
  los dos valores van vacios por defecto y se derivan del helper que ya saneaba los nombres.
- **G51 · Una etiqueta de mas de 63 caracteres tumba el despliegue entero.** `app.kubernetes.io/version`
  se construia con `.Values.image.tag`, y es la UNICA etiqueta que sale de un value — por eso se escapo
  del `trunc 63` que si tenian los nombres. Basta con una herramienta que etiquete la imagen con el
  digest (Skaffold lo hace: 64 caracteres) para que el API server rechace ConfigMap, Service y
  Deployment a la vez con «must be no more than 63 characters». Regla: **todo lo que llegue a un nombre
  o a una etiqueta desde un `value` pasa por `trunc 63`**, no solo lo que construyen los helpers.
- **G52 · El fichero de compose se llamaba `compose-app.yml`, un nombre que no busca nadie.** El `-app`
  venia de querer separarlo de otros compose y acabo sobrando. Ahora es `compose.yaml`, que es el nombre
  preferido por la especificacion de Compose Y el primero que busca Spring Boot (junto a `compose.yml`,
  `docker-compose.yaml` y `docker-compose.yml`). Con eso se puede **borrar** `spring.docker.compose.file`,
  y el mensaje de error mejora: sin esa propiedad, Boot dice «No Docker Compose file found in directory
  '...'» —nombrando el directorio donde busco, que es el dato que hace falta porque la busqueda es
  relativa al directorio de trabajo—; con la propiedad solo dice que ese fichero debe existir.
- **G53 · Todos los proyectos generados pedian los MISMOS puertos del host.** El compose publicaba
  `5432:5432`, `6379:6379` y los de Jaeger, asi que levantar un segundo microservicio moria con «port is
  already allocated». Pasaba desapercibido mientras el compose se levantaba a mano; al hacer que Spring
  Boot lo levante solo, se convirtio en que el segundo servicio no arranca. Arreglado en dos mitades,
  porque no todos los puertos son iguales:
  **Postgres y Redis** pasan a puerto aleatorio (`- '5432'` sin parte de host) y **Spring Boot lo
  descubre**: su soporte de compose lee el puerto publicado y arma la conexion. Medido con dos proyectos
  a la vez: 32803 y 32806, sin colision. Para conectarte tu, `docker compose port db-app-postgres 5432`.
  **Jaeger no puede**, porque su 16686 es una interfaz que abres en el navegador y su 4318 va escrito en
  la configuracion (Boot tiene descubrimiento para bases de datos y Redis, pero NO para OTLP). Va detras
  de un perfil de compose: por defecto Spring lo activa y tienes trazas sin hacer nada; para el segundo
  servicio, `SPRING_DOCKER_COMPOSE_PROFILES_ACTIVE= make run` y exporta al colector del primero, que es
  lo que quieres de todas formas.
- **G54 · `maven.build.timestamp.format`: configuracion heredada que no usaba nadie.** Estaba en el pom
  de la plantilla con el formato `yyyy_MM_dd_HH_mm_ss` — que es la pinta clasica del apaño para evitar los
  dos puntos del formato ISO por defecto, que rompen rutas y algunas herramientas. Pero **nada** en el
  proyecto referencia `${maven.build.timestamp}`, y no hay ni rastro de Sonar. Configuracion que apunta
  a un problema que este proyecto no tiene: fuera. Si algun dia se añade Sonar y el bug sigue vivo, se
  vuelve a poner con un comentario que diga por que.
- **G29 · Helm: los nombres de objeto deben ser RFC 1123 (minusculas), y `regexReplaceAll` no encadena.**
  Dos fallos en el mismo helper, los dos silenciosos. Primero: un `artifactId` en camelCase genera objetos
  que Helm renderiza sin quejarse y que **el API server rechaza al desplegar** — el fallo aparece en el
  peor momento y lejos de donde se causo. Segundo: `regexReplaceAll` toma `(regex, INPUT, replacement)`,
  asi que usarlo en una tuberia mete el valor como *replacement* y el nombre sale **vacio**, tambien sin
  error. Los dos los caza `helm lint`, que por eso conviene ejecutar y no dar por bueno el template.
- **G28 · `<release>` de `maven-metadata.xml` INCLUYE pre-releases.** Preguntando por la ultima version
  salian Boot `4.2.0-M1`, MapStruct `1.7.0.Beta2` y jar-plugin `4.0.0-beta-1`. Hay que filtrar
  (`grep -viE "alpha|beta|-M[0-9]|-RC|snapshot"` sobre `<version>` y quedarse con la ultima), o se
  acaba fijando un milestone en la base de todos los servicios. Complementa a G1.
- **G26 · Un `application.yaml` en `src/test/resources` TAPA al de `src/main/resources`.** Mismo nombre,
  y el classpath de test va primero: Spring carga el primero que encuentra y el principal no se lee
  NUNCA. Consecuencia: la configuracion de la aplicación (nombre, actuator, logging) no se aplicaba en
  los tests, y no hay forma de notarlo — los tests pasan igual. Se resuelve nombrandolo
  `application-test.yaml`, que se **superpone** en vez de sustituir.
- **G27 · springdoc no garantiza el orden de las claves del OpenAPI.** Dos ejecuciones seguidas
  intercambiaban `first` y `last`. Un test de contrato que parpadea sin que nadie cambie nada acaba
  desactivado, asi que hay que normalizar de verdad: `ORDER_MAP_ENTRIES_BY_KEYS` **no ordena un
  `JsonNode`**, solo mapas — hay que deserializar a `Object`.
- **G22 · Postgres 18 cambió el punto de montaje del volumen.** Espera **un solo** montaje en
  `/var/lib/postgresql` (los datos van en un subdirectorio), no en `/var/lib/postgresql/data` como antes.
  Con el punto antiguo el contenedor arranca, falla y muere — y el síntoma que ves es **la aplicación
  quejándose de que no encuentra `DataSource`**, que no apunta a esto por ningún lado. Lo tuvimos roto y
  no se veía porque los tests usan Testcontainers, que no monta volumen: `make verify` estaba en verde y
  `make run` no arrancaba.
- **G23 · `spring.docker.compose.file` es una ruta RELATIVA al directorio de trabajo.** Al pasar a
  multi-módulo, la app se arranca con `mvn -pl app`, el proceso corre desde `app/` y dejaba de encontrar
  el `compose.yaml` de la raíz. Mismo síntoma engañoso que G22. Se resolvió declarando la conexión
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

- [x] **Modernizar versiones.** ✅ 2026-09-09. Spring Boot 4.0.2 → **4.1.1**, Spring Cloud 2025.1.0 →
      2025.1.3, springdoc 3.0.1 → 3.1.1; y en el pom del propio arquetipo: archetype-plugin 3.2.1 →
      3.4.1, jar 3.4.2 → 3.5.1, gpg 3.1.0 → 3.2.8, central-publishing 0.9.0 → 0.11.0.
      ⚠️ **Las versiones se sacan filtrando pre-releases**: `<release>` de `maven-metadata.xml` incluye
      milestones y betas (decia Boot `4.2.0-M1`, MapStruct `1.7.0.Beta2`, jar-plugin `4.0.0-beta-1`).
      Verificado ademas arrancando la aplicacion, no solo con el gate: arranca en 4s, la seguridad
      responde 401/200/201 y `/actuator/prometheus` sigue protegido.
- [x] **CI y el gate.** ✅ 2026-09-09. `make verify` es ahora EL GATE (clean → build → generate → check →
      `mvn verify` del generado) y `.github/workflows/verify.yml` corre **exactamente ese comando** en cada
      push a `main`, cada PR, a mano y semanalmente. Que sea el mismo comando no es estética: si el gate
      local y el CI divergieran, el CI dejaría de significar nada.
      Además `scripts/check-generated.sh` convierte seis gotchas en comprobaciones automáticas (C1..C6, ver
      [flujo-de-trabajo.md](flujo-de-trabajo.md)), **todas verificadas rompiendo el proyecto a propósito**:
      una comprobación que nunca falla no vale nada.
      De paso murió `restart_build.bash` (duplicado literal del Makefile) y el `MAKEFILE.md` de la raíz
      (509 líneas documentando targets que ya no existían).
- [x] **Decidir sobre `k8s/` y skaffold.** ✅ 2026-09-09 (D10): fuera skaffold, y el despliegue pasa a un
      chart de Helm con values por entorno. Verificado con `helm lint` y `helm template` de verdad.
- [ ] **Autoconfiguración para el cliente Java.** Hoy `client-java` es código generado en crudo: quien lo
      use tiene que instanciar el `ApiClient`, ponerle la URL base y cablear el token a mano, en cada
      proyecto consumidor. Con un `@AutoConfiguration` + `@ConfigurationProperties` dentro del módulo,
      consumirlo sería añadir la dependencia y poner dos propiedades. **Es lo que separa "te damos un
      cliente generado" de "te damos un cliente que se usa solo"**, y es el mayor salto de comodidad que
      le queda al módulo.
- [ ] **Indicador de salud del cliente.** Que el cliente aporte un `HealthIndicator`, para que el
      servicio que lo consume vea en su propio `/actuator/health` si su dependencia responde. Sin eso, un
      consumidor se entera de que el servicio del que depende está caído cuando le falla una petición de
      usuario.
- [ ] **Módulo de ejemplo ejecutable (`sandbox`).** Un microservicio mínimo que consuma el cliente
      generado. Sirve de documentación viva y, sobre todo, **ejercita el cliente**: hoy comprobamos que
      se genera y compila, no que se pueda usar de verdad.
- [ ] **`@Version` para bloqueo optimista.** El manejador de errores ya traduce
      `ObjectOptimisticLockingFailureException` a un 409, pero ninguna entidad de ejemplo lo usa: la rama
      está escrita y no se ejercita nunca. Una entidad base con `@Version` cerraría el círculo.
- [ ] **Pruebas de carga.** Un `docker/k6/` con un par de escenarios. Valor real solo si se ejercitan; si
      no, es andamio (ver el criterio nº2 de este documento).
- [ ] **Imagen nativa (GraalVM).** Arranque en milisegundos y mucha menos memoria, que en Kubernetes se
      nota en la factura. Cuesta un `compose` aparte y disciplina con la reflexión.
- [ ] **Parent POM publicado** (D4), ya en segunda fase.
