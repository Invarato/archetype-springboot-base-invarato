# Flujo de trabajo

Cómo se trabaja en este repo y, sobre todo, **qué es estar en verde**.

Para la lista de objetivos, `make help`. Aquí va lo que `make help` no puede contar: por qué existe el gate
y qué caza cada comprobación.

## La trampa de este repo

`mvn install` sobre el arquetipo da **`BUILD SUCCESS` aunque el proyecto que genera no compile**. Y no es un
descuido: aquí solo se empaquetan ficheros de texto, no se compilan. El arquetipo llegó a estar generando un
proyecto roto durante mucho tiempo sin que nadie lo viera, precisamente porque su propio build estaba verde.

De ahí la regla que ordena todo lo demás:

> **El único verde que cuenta es el del proyecto generado.**

## El gate

```bash
make verify
```

Es lo que hay que tener en verde antes de dar por bueno cualquier cambio, y es **exactamente** lo que corre
el CI (`.github/workflows/verify.yml`). Si el gate local y el CI divergieran, el CI dejaría de significar
nada.

Hace cuatro cosas, en este orden:

1. `clean` — borra el arquetipo de `~/.m2` y el proyecto generado anterior. Importante: sin esto,
   `archetype:generate` puede resolver una versión vieja de la caché y estarías probando lo de ayer.
2. `build` — instala el arquetipo en `~/.m2`.
3. `generate` — genera un proyecto de prueba **fuera del repo** (por defecto en el directorio padre).
4. `check` + `test-generated` — las comprobaciones estructurales y `mvn verify` sobre lo generado.

Mientras editas, `make rebuild` (los pasos 1–3) es más rápido. Pero no lo confundas con estar en verde.

## Las comprobaciones estructurales

`scripts/check-generated.sh`, que se invoca con `make check`. **Cada una corresponde a un fallo real que ya
nos comimos**, no a una hipótesis; los códigos `G*` remiten a
[base-del-proyecto.md](base-del-proyecto.md).

| | Qué comprueba | El fallo que caza |
|---|---|---|
| **C1** | No quedan `${groupId}` / `{groupId}` sin resolver | Cinco ficheros de test salían con líneas que empezaban por `{groupId}.` porque a la plantilla le faltaba el prefijo `import $`. Compilaba en el arquetipo (es texto) y reventaba al generar. |
| **C2** | Los `.md` generados tienen las **mismas líneas** que su plantilla | G17: en un fichero filtrado, Velocity trata `##` como comentario de línea. `MAKEFILE.md` llegaba con 54 líneas menos y **cero cabeceras**; `SKAFFOLD.md`, con 76 menos. El fichero existe y casi todo el texto sigue ahí, así que a simple vista parece correcto. |
| **C3** | Los `devcontainer.json` parsean | G20: los que se enviaban tenían las claves **sin comillas**. No eran JSON válido, así que ese devcontainer nunca llegó a abrir. |
| **C4** | `mvnw` es ejecutable y tiene sus `.mvn/wrapper/*.properties` | G16 y G18: se copiaba el script sin su configuración, el `.gitignore` excluía `/.mvn/` entero, y los arquetipos no conservan el bit de ejecución. |
| **C5** | El chart de Helm está completo y su directorio no conserva placeholders | G6, dos veces: primero un `skaffold.yaml` apuntando a un `k8s/` que no viajaba; después, los perfiles staging y prod de ese mismo skaffold apuntando a un chart de Helm que no existió nunca. |
| **C6** | No viajan ficheros muertos conocidos | `__gitignore` y `old__Dockerfile` se generaban sin que nadie los usara. |
| **C7** | El contrato viaja y **no publica entidades** | El cliente Java generado traía una clase `MyTable` con la forma de la tabla: un cambio en la base de datos se habría convertido en un cambio de la API, y de ahí a todos los consumidores. |
| **C8** | Los clientes generan código de verdad | Un generador mal configurado no falla: no genera nada, el módulo compila vacío y quien consume se encuentra un jar sin clases. |
| **C9** | Los tests usan la **misma versión de Redis** que el compose | Estuvieron descuadradas (tests con `redis:7-alpine`, entorno real con `redis:8`). Es el peor descuadre posible: la suite da verde sobre un motor distinto del que se despliega. |
| **C10** | El ejemplo de `client-python` es Python válido | Es código que se publica y que **nadie ejecuta en el build**: sin esto, un paréntesis mal puesto viajaría a todos los proyectos generados. Se salta si no hay `python3` (el runner de CI sí lo tiene). |
| **C11** | El cliente **Python** también genera código | C8 solo miraba el de Java: el módulo de Python podía estar generando cero módulos sin que nadie se enterase. |
| **C12** | El ejemplo de Python usa una API que **existe** | Renombrar un método de un controlador cambia su `operationId` y, con él, los nombres del cliente generado (G31). Sin esto, el ejemplo quedaría llamando a métodos que ya no existen. Es lo que `ClienteGeneradoTest` hace por el lado Java, pero sin necesitar un intérprete de Python. |

### Dos fases, y por qué

`check` corre **antes** del build y `check-post` **después**:

```
make verify  =  rebuild → check → mvn verify del generado → check-post
```

No es una floritura. **C8 nunca llegó a ejecutarse**: necesita el código generado en `target/`, pero
`check` corría antes de construir nada, así que su guarda `if [ -d ... ]` la saltaba en silencio — y el
resumen seguía diciendo que estaban todas OK. Ahora las que dependen del build viven en la fase `post`, y
el total del resumen **se cuenta** en vez de escribirse a mano, que es lo que permitió que el número
mintiera durante semanas.

⚠️ **Una comprobación que nunca falla no vale nada.** Si añades una, pruébala rompiendo el proyecto generado
a propósito y comprobando que se pone roja. Las de arriba se verificaron así — salvo C10, cuyo camino de
comprobación real solo se ejercita donde hay `python3`; aquí solo se verificó que se salta bien.

## Variables

Todas se pueden sobreescribir en la línea de comandos:

| Variable | Por defecto | Para qué |
|---|---|---|
| `ARTIFACT_ID` | `projectTestForAnalysis` | Nombre del proyecto de prueba |
| `GROUP_ID` | `com.jarroba.prueba` | Grupo del proyecto de prueba |
| `OUT_DIR` | el directorio **padre** del repo | Dónde se genera. El CI lo apunta a su directorio temporal |
| `ARCHETYPE_VERSION` | la del `pom.xml` | Para probar contra otra versión |

```bash
make generate ARTIFACT_ID=miPrueba GROUP_ID=com.ejemplo
make verify OUT_DIR=/tmp/pruebas
```

⚠️ **`OUT_DIR` por defecto sale de un `dirname`**, así que desde un git worktree el proyecto se genera en el
padre *del worktree*, no en el del repo. `make clean` sólo borra ese directorio si de verdad parece un
proyecto generado (tiene `pom.xml` y `app/`); si no, avisa y no toca nada.

## Publicar

```bash
make publish
```

Pide escribir la versión para confirmar, porque **publicar en Maven Central es irreversible**: una versión
publicada no se puede borrar ni reemplazar. Requiere la clave GPG importada y las credenciales del portal de
Sonatype en `~/.m2/settings.xml`. Los detalles están en el `readme.md` de la raíz.

El día a día va con `-Dgpg.skip=true`, que ya lo pone `make build`.

## Integración continua

`.github/workflows/verify.yml` corre `make verify` en cada push a `main`, en cada PR, a mano
(`workflow_dispatch`) y **una vez por semana**. Lo semanal no es manía: aquí casi todas las versiones las
gestiona el BOM de Spring Boot, así que el build puede romperse sin que nadie toque el repo — mejor
enterarse un lunes que al ir a publicar.

Si falla, sube como artefacto los informes de surefire/failsafe del proyecto generado, que viven en el
directorio temporal del runner y si no se perderían.
