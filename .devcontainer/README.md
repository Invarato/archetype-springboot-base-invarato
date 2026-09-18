# Devcontainer — archetype-springboot-base-invarato

Entorno para trabajar **en el arquetipo**, no en un proyecto Spring Boot. La diferencia manda en todo
lo que hay aquí: este repo tiene `packaging: maven-archetype`, y el ciclo de trabajo es *compilar el
arquetipo → instalarlo en `~/.m2` → generar un proyecto de prueba → mirar si ese proyecto compila,
pasa los tests y arranca*.

Está pensado para que **trabajen agentes de IA dentro**, así que la caja no es solo comodidad: hay un
modelo de amenaza explícito, y está más abajo con sus límites dichos en voz alta.

## Un solo perfil, y con Docker

Hubo dos perfiles: uno *hardened* sin Docker (el de por defecto, para los agentes) y otro
*with-docker* privilegiado. **Se colapsaron en uno solo, con Docker**, y conviene saber por qué,
porque es una decisión de seguridad revisada a la baja a propósito.

**El motivo:** sin Docker no se puede comprobar lo único que importa aquí. El proyecto generado
necesita un demonio Docker para sus tests de integración (Testcontainers) y para su `compose.yaml`.
Un entorno donde solo se puede compilar deja el `mvn verify` sin correr — que es justo donde viven los
fallos caros. Y el arquetipo se vende como *«descárgalo y trabaja con agentes»*: si la caja donde se
desarrolla no puede correr Docker, esa promesa es falsa.

**Lo que se pierde, dicho claro:** Docker-in-Docker exige `privileged`, y un contenedor privilegiado
**no aísla del anfitrión** (aquí, tu VM de WSL). Se miraron las alternativas y ninguna sale gratis hoy:

| Alternativa | Por qué no |
|---|---|
| **DinD rootless** | Issue **abierto** en la spec de devcontainers (#479): fallan las escrituras al bind mount salvo corriendo como root dentro. Sin receta verificada de extremo a extremo. |
| **Podman rootless** | Obliga a `TESTCONTAINERS_RYUK_DISABLED=true` → adiós a la limpieza automática de contenedores. Más rarezas de red y de permisos del socket. |
| **docker-outside-of-docker** | El socket del anfitrión **es root en el anfitrión**, y mezcla los contenedores efímeros de los tests con los de trabajo real. Peor, no mejor. |
| **Testcontainers Cloud** | Sí evitaría el demonio local, pero es una dependencia externa y una cuenta de terceros. |

**La conclusión que ordena todo lo demás:** el contenedor nunca fue la frontera real. El repo va
montado en lectura/escritura y el token de `gh` vive dentro — con o sin privilegios. La contención de
verdad es **dónde** corre esto (una VM que puedas tirar), **el alcance del token** y **tu revisión del
diff**. Lo que sí se conserva es el **firewall de salida**, porque no detiene a un agente hostil pero
sí detiene el fallo dominante: el despiste.

## Qué trae la imagen

| Pieza | Por qué |
|---|---|
| **JDK 25 + Maven 3.9.x** (feature `java`) | 25 es lo que fija el pom de la plantilla; el arquetipo se prueba con el mismo JDK que usarán sus proyectos. Maven cumple el mínimo del readme (3.6.3+). |
| **`make`** | El flujo del repo *es* el `Makefile` (`make rebuild`). La imagen base no lo trae. |
| **`gnupg`** | Firmar artefactos al publicar en Maven Central (`maven-gpg-plugin`). |
| **GitHub CLI** + git cableado a su token | PRs y releases desde dentro, sin pelearse con credenciales. |
| **Claude Code** (binario nativo) | Instalado desde el `Dockerfile`, **no** por la feature: esa instala vía npm y metería Node en un repo JVM puro. |
| **iptables · ipset · dnsmasq** | El firewall de salida. Van en la imagen porque el firewall no puede depender de que haya red para poder levantarse. |

**No hay Postgres, Redis ni MinIO**, y es a propósito: quien los necesita es el *proyecto generado*,
que se trae su propio compose.

## Dónde cae el proyecto generado

`make generate` escribe el proyecto (`dirname $PWD`, configurable con `OUT_DIR`) en el **directorio
padre** del repo. Por eso el repo **no** se monta en `/workspace`, sino un nivel por debajo:

```
/workspace/
├── archetype-springboot-base-invarato/   ← el repo (bind mount del host)
└── projectTestForAnalysis/               ← lo que genera `make rebuild`
```

Si el repo se montara directamente en `/workspace`, el padre sería `/` y la generación fallaría.

⚠️ **El proyecto generado vive solo dentro del contenedor**, no aparece en el host. Es correcto: es de
usar y tirar (`make clean` lo borra en cada ciclo) y así el directorio de proyectos del host no se
llena de copias.

## Worktrees de los agentes

Cada agente se crea el suyo en **`/home/vscode/worktrees/<nombre>`**:

```bash
cd /workspace/archetype-springboot-base-invarato
git worktree add -b agente-1 /home/vscode/worktrees/agente-1
```

Están **fuera del repo** a propósito: dentro, el IDE indexaría varias copias del mismo proyecto y
cualquier búsqueda devolvería todo por duplicado. Y están en un **volumen**, no en una carpeta
cualquiera del contenedor, porque si no se los lleva por delante el primer rebuild y con ellos el
trabajo a medias.

Funciona porque git enlaza worktree y repo por **ruta absoluta** en los dos sentidos
(`<worktree>/.git` → `/workspace/<repo>/.git/worktrees/<n>` y vuelta), y las dos rutas se mantienen
entre rebuilds: una es el bind mount del repo y la otra el volumen. *(Verificado: se crea un worktree,
se cambia de imagen y siguen ahí el fichero, la rama y `git worktree list`.)*

⚠️ Ojo con una consecuencia: desde un worktree, `make generate` escribe en **su** padre, o sea en
`/home/vscode/worktrees/`, no en `/workspace/`.

## Qué sobrevive a un "Rebuild Container"

**`$HOME` pertenece al contenedor: cada rebuild lo borra entero.** Solo persiste lo que esté en un
volumen. Y lo caro de esto no es la pérdida, es *cuándo* se descubre: nunca en un momento tranquilo,
sino al ir a mirar por qué el CI está rojo y no poder, o al ir a publicar con el tag ya empujado. Por
eso están montados **todos de una vez** y no según vaya doliendo — cada tanda cuesta un rebuild.

| Volumen | Ruta | Qué guarda |
|---|---|---|
| `archetype-invarato-m2` | `~/.m2` | Caché de Maven **y** donde `mvn install` deja el arquetipo para que `archetype:generate` lo encuentre. También tu `settings.xml`. |
| `archetype-invarato-claude` | `~/.claude` | Todo el estado de Claude Code (ver abajo). |
| `archetype-invarato-worktrees` | `~/worktrees` | Los worktrees de los agentes. |
| `archetype-invarato-gh` | `~/.config/gh` | Token de GitHub. |
| `archetype-invarato-history` | `~/.commandhistory` | Historial de bash y zsh. |
| `archetype-invarato-gnupg` | `~/.gnupg` | Clave de firma para Maven Central. |
| `archetype-invarato-ssh` | `~/.ssh` | Claves SSH. |
| `archetype-invarato-docker` | `~/.docker` | Sesión de `docker login` y estado de buildx. |

Los nombres son **fijos** y no llevan `${devcontainerId}` a propósito: ese id se deriva de las
etiquetas que identifican al devcontainer (carpeta local + fichero de configuración), así que cualquier
cambio en la configuración te costaría la caché de Maven, la sesión de Claude y los worktrees. El
precio de los nombres fijos: dos clones del repo en la misma máquina los comparten.

⚠️ **`~/.ssh` y `~/.gnupg` están montados y al alcance de los agentes.** Cuando había dos perfiles,
vivían solo en el tuyo; ahora que hay uno, la separación desapareció. Monta ahí **solo** la clave GPG
de este proyecto —no tu llavero entero— y ten en cuenta que para el trabajo normal no hacen falta: el
remote es HTTPS y se empuja con el token de `gh`.

⚠️ **La primera vez, el volumen nace vacío** — así que en *ese* rebuild se pierde igual todo lo que
estás haciendo persistente. La persistencia empieza a partir de ahí. Es el malentendido más probable
de todo esto: parece que el arreglo no funciona, y lo que pasa es que aún no había nada dentro.

### La primera vez, una sola vez

Después del rebuild que estrena los volúmenes:

```bash
git worktree prune                          # limpia registros huérfanos que quedaron en .git

# El token de GitHub. Así, y no `echo <token> | gh auth login`: el historial de shell AHORA ES UN
# VOLUMEN, así que un token escrito en la línea de comandos sobrevive a los rebuilds ahí dentro.
read -rs GH_TOKEN && echo "$GH_TOKEN" | gh auth login --with-token && unset GH_TOKEN
gh auth status                              # queda guardado en el volumen ~/.config/gh
# y, si los usas:
gpg --import clave-privada.asc
chmod 700 ~/.ssh && chmod 600 ~/.ssh/<clave>
docker login
```

Del siguiente rebuild en adelante, nada de esto hace falta.

### Lo que sigue sin sobrevivir (dicho también)

- **Lo que instales a mano dentro del contenedor.** Regla: *volumen para lo que se genera, Dockerfile
  para lo que se instala*. Con `sudo` completo SÍ puedes instalar a mano, pero se perderá en el
  siguiente rebuild: lo que deba quedarse va al `Dockerfile`.
- **Borrar el volumen** (`docker volume rm archetype-invarato-gh`). Que es, precisamente, la forma
  limpia de sacar una credencial de aquí.
- **El proyecto generado** en `/workspace/` — de usar y tirar, `make clean` lo borra en cada ciclo.
- **Las extensiones del IDE**: se reinstalan solas en cada rebuild. Persistirlas da más problemas
  (versiones desparejadas) que los minutos que ahorra.
- **Mover o renombrar la carpeta del proyecto en el host**: cambia el bind mount del repo. Los
  volúmenes siguen (nombres fijos), pero los worktrees quedarían apuntando a una ruta que ya no
  existe → `git worktree prune` y recrearlos.

### El detalle de Claude Code que no es obvio

Claude Code guarda su estado en **dos sitios**: el directorio `~/.claude` (credenciales, `settings.json`,
transcripciones en `projects/`, sesiones, plugins) y el fichero **`~/.claude.json`** (cuenta, historial
por proyecto, MCP). Ese fichero queda **fuera** de un volumen montado en `~/.claude`, así que un
volumen "de Claude" a secas pierde media sesión en cada rebuild — y no se arregla con un symlink,
porque el fichero se reescribe con *temp + rename* y el enlace acaba sustituido por un fichero normal.

La solución está en el `Dockerfile`: **`ENV CLAUDE_CONFIG_DIR=/home/vscode/.claude`**. Con eso el
`.claude.json` se escribe dentro del directorio y entra en el volumen. *(Verificado: se escribe
estado, se cambia de imagen y el `.claude.json` sigue ahí.)*

### Comprobar que los montajes están de verdad

Más fiable que fiarse de que el rebuild "fue bien":

```bash
mount | grep -E "worktrees|\.config/gh|\.m2|\.claude|commandhistory"
#   → cada uno debe aparecer sobre un dispositivo; si no sale, ese volumen NO se montó

ps -o lstart= -p 1          # cuándo arrancó este contenedor
ls -la ~/.m2                # ¿hay ficheros ANTERIORES a esa fecha? → el volumen ha sobrevivido
```

Antes de un rebuild, comprueba que no dejas trabajo sin salvar en ningún worktree:

```bash
for wt in ~/worktrees/*/; do
  [ -d "$wt" ] || continue
  echo "$(basename $wt): $(git -C "$wt" status --porcelain | wc -l) sin commitear, \
$(git -C "$wt" log --oneline origin/main..HEAD 2>/dev/null | wc -l) commits fuera de main"
done
```

Si sale algo distinto de `0 / 0`, commitea y sube antes de seguir. Y que no cunda el pánico: **las
ramas y todo lo commiteado no se pierden nunca** — viven en `<repo>/.git`, que es bind mount del host.
Lo que se pierde son las carpetas y, con ellas, lo que no esté commiteado.

## Modelo de amenaza (leer antes de dejar agentes solos)

**De qué protege:** de un agente que se equivoca, que sigue instrucciones que le han colado en un
fichero que leyó, o que se trae dependencias de donde no debe. Contra eso queda una cosa, y es real:
**la salida a internet va por allowlist**, así que un despiste no puede traerse ni mandar algo a
cualquier sitio.

**De qué NO protege:**

- **El contenedor es privilegiado.** Docker-in-Docker no existe sin eso, y sin Docker no se puede
  comprobar el proyecto generado. Un contenedor privilegiado se sale al host —aquí, tu VM de WSL con
  todo tu `$HOME`— sin esfuerzo, y el firewall se puede desmontar desde dentro: es un **guardarraíl
  contra un despiste, no una barrera contra un agente hostil**.
  **La consecuencia práctica:** si vas a dejar agentes trabajando solos, la contención no la pone esta
  caja — ponla fuera: una **VM que puedas tirar**, un **token de alcance reducido** y **revisar el
  diff**. Ver *Un solo perfil, y con Docker* arriba para las alternativas que se descartaron y por qué.
- **El repo está montado en lectura/escritura.** Tiene que estarlo. Un agente puede cambiar cualquier
  fichero del repo, incluido este devcontainer — el control es tu revisión del diff, no el contenedor.
- **El token de `gh` está a mano de los agentes.** Es lo que les permite abrir PRs. Usa un token
  *fine-grained* limitado a este repo, no tu token personal de todo GitHub. Permisos sobre este repo:
  **Metadata** (read, obligatorio y lo pone solo), **Contents** (read+write, para `git`),
  **Pull requests** (read+write, para `gh pr create` — sin esto los agentes pueden empujar ramas
  pero no abrir el PR), **Actions** (read, para consultar el CI) y **Workflows** (read+write **solo**
  si vas a tocar `.github/workflows/`; sin él, el push se rechaza *solo* por esos ficheros, con un
  error que no menciona el token). Dar **Actions: read+write** permite además *lanzar* workflows
  desde dentro — y lo puede usar cualquiera que entre al contenedor.
- **DNS.** El puerto 53 sale (a través del resolver local) y sigue siendo un canal de fuga lento.
  Cerrarlo del todo pediría un resolver que además filtrase por nombre.
- **`~/.claude/settings.json` está en un volumen escribible.** Un agente puede cambiar su propia
  configuración, hooks incluidos.
- **El historial de shell persiste.** Es cómodo, pero convierte cualquier secreto que teclees en la
  línea de comandos en un secreto guardado: escríbelos por `read -rs` o por fichero, nunca inline.

### El firewall de salida

`init-firewall.sh` corre como `postStartCommand` en **cada arranque** (las reglas viven en el namespace
de red, que es nuevo cada vez) y con `waitFor` puesto: el IDE espera a que termine, así que nadie
trabaja antes de que esté. Se verifica a sí mismo al final —comprueba que un dominio prohibido está
bloqueado y uno permitido responde— y **falla ruidosamente**, porque un firewall que se cree activo y
no lo está es peor que no tenerlo.

**Cómo funciona.** No resuelve los dominios "una vez y a correr": eso no funciona. Medio internet está
detrás de CDNs y balanceadores que devuelven IPs distintas *en cada consulta* — Docker Hub es el caso
de libro, dos `dig` seguidos dan conjuntos casi disjuntos. Una lista fija de IPs acierta a veces y
falla otras, que es la peor forma de fallar. Aquí el DNS del contenedor pasa por un **dnsmasq local**
que mete en el ipset la IP de cada respuesta de un dominio permitido, justo antes de devolvérsela a
quien preguntó. Lo que se permite es el **nombre**; la IP la descubre el propio DNS. De regalo, cubre
subdominios: `github.com` vale para `api.`, `codeload.`, `gist.`…

**Para añadir un dominio:** se edita la lista `ALLOWED_DOMAINS` dentro de `init-firewall.sh` y se hace
*Rebuild Container*. No hay forma de ampliarla desde dentro, y es deliberado: el script es `root:root`
y el perímetro se decide fuera de la caja. Un fichero de allowlist editable por quien vive dentro
convierte el firewall en decoración.

**Está permitido:** Maven Central y Sonatype · GitHub y githubusercontent · ghcr.io · api.anthropic.com
y claude.ai · Docker Hub y su CDN · la marketplace de VS Code y sus CDNs · Spring.
**Comentado, por si usas IntelliJ:** `jetbrains.com`.

### Nota histórica: las capabilities del antiguo perfil hardened

Ya **no aplica** (Docker-in-Docker corre privilegiado y no hay `cap-drop`), pero se deja anotado porque
se pagó descubriéndolo y ahorraría el trabajo a quien algún día intente volver a una caja sin Docker:

- `NET_ADMIN`, `NET_RAW` — iptables/ipset. Sin ellas el firewall no hace nada.
- `SETUID`, `SETGID` — sin ellas `sudo` muere con *"unable to change to root gid"*: el bit setuid da
  euid=0, pero cambiar de gid es una capability aparte.
- `AUDIT_WRITE` — `sudo` aborta al inicializar su plugin de auditoría.
- `KILL` — para reiniciar dnsmasq, que corre como `nobody`: root **sin** esta capability no puede
  señalar a un proceso de otro usuario. Sin ella el firewall fallaba en el **segundo** arranque del
  contenedor y el primero iba bien, que es como no enterarte hasta que ya confías en él.
- **No** usar `--security-opt=no-new-privileges` aunque suene a lo correcto: bloquea el bit setuid y con
  él se lleva `sudo`, que es justo como se lanza el firewall. Hardening que desactiva el hardening.

## Uso

```bash
make help          # ver objetivos y variables
make rebuild       # clean + build + generate  ← el ciclo normal
make build         # solo mvn clean install (con -DskipTests -Dgpg.skip=true)
make generate ARTIFACT_ID=miPrueba

# probar el resultado (Testcontainers usa el Docker de dentro)
cd ../projectTestForAnalysis
make test          # Testcontainers usa el Docker de dentro
make run           # API en 8080, ya reenviado al host
```

## Publicar una versión

El día a día va con `-Dgpg.skip=true`. Para publicar:

```bash
# ⚠️ Primero el rebuild, DESPUÉS importar: el volumen nace vacío y se monta
# encima de lo que hubiera, así que una clave importada antes del rebuild queda tapada.
gpg --import clave-privada.asc          # una vez; queda en el volumen ~/.gnupg
# ~/.m2/settings.xml con <server id="central"> y el perfil ossrh (gpg.passphrase)
mvn clean install central-publishing:publish
```

💡 Si prefieres no dejar el token de Sonatype escrito en el volumen, pon
`<password>${env.CENTRAL_TOKEN}</password>` en `settings.xml` y exporta la variable solo en la sesión
en la que publicas.

## Cuando algo falla

- **Instalé algo con `apt-get` y tras el rebuild ya no está** — esperado: `$HOME` y el sistema de
  ficheros del contenedor se rehacen en cada rebuild. Regla: *volumen para lo que se genera, `Dockerfile`
  para lo que se instala*.
- **`docker` no responde / `Cannot connect to the Docker daemon`** — dockerd arranca con el contenedor y
  tarda un poco. Si persiste, mira el log de la feature; el firewall no lo bloquea (escribe en
  `DOCKER-USER` y no hace `iptables -F`, justo para no dejar sin red a los contenedores de dentro).
- **`toomanyrequests` de Docker Hub a mitad de un `make verify`** — no es un problema de credenciales
  *aparente*, pero lo es: estás tirando como anónimo. `docker login` (queda en el volumen `~/.docker`).
- **Una descarga se queda colgada** — probablemente sea el firewall. Compruébalo con
  `curl -v https://<host>` y, si el dominio es legítimo, añádelo a `ALLOWED_DOMAINS` y *Rebuild*.
- **Una extensión del IDE no se instala** — mismo motivo. La marketplace y sus CDNs ya están en la
  lista; si aparece un host nuevo, añádelo. Para salir del paso puedes comentar `postStartCommand` y
  `waitFor` en el `devcontainer.json`, instalarla, y volver a activarlos.
- **`git push` responde 403 aunque `gh auth status` diga que estás autenticado** — no es el token: es
  git usando otro credential helper. Ya está cableado en el `Dockerfile` (config de sistema, no
  `~/.gitconfig`, porque `$HOME` no es volumen).
  ⚠️ Y mete el token **solo en `gh`** (`gh auth login --with-token`, que escribe en
  `~/.config/gh/hosts.yml`). Si lo pegas en el **inicio de sesión de GitHub del IDE**, sustituyes la
  credencial que el IDE le reenvía a git y te quedas sin `fetch`/`push` con ese mismo *403: Write
  access not granted* — en un *fetch*, que es una lectura.
- **`ssh` pide contraseña con una clave que acabas de pegar** — son los
  permisos, aunque no lo diga: el directorio necesita `700` y la clave `600`, o `ssh` la descarta en
  silencio. `chmod 600 ~/.ssh/<clave>` y compruébala con
  `ssh-keygen -y -f ~/.ssh/<clave> >/dev/null && echo válida`.
  ⚠️ Y el orden va al revés de lo que parece: **reconstruye primero y pega la clave después**. El
  volumen nace vacío y se monta *encima*; si reconstruyes con la clave ya puesta, el montaje la tapa.
- **Tu uid en el host no es 1000** — el ajuste automático de uid que hace Dev Containers puede dar
  guerra con los volúmenes. Se arregla con `"updateRemoteUserUID": false`.
- **Empezar de cero con las credenciales** — `docker volume rm archetype-invarato-gh`
  (y `-claude`, `-gnupg`, `-m2`, `-worktrees` según lo que quieras tirar).

## Notas

- ⚠️ **Hay DOS `.devcontainer/` en este repo y no tienen nada que ver.** Este es el de *trabajar sobre
  el arquetipo*. El de `src/main/resources/archetype-resources/.devcontainer/` es **la plantilla que se
  copia a los proyectos generados**. Tocar uno no cambia el otro. (Antes eran `base/` y `full/`; se
  colapsaron en uno solo, también con Docker, porque el proyecto generado lo necesita para
  Testcontainers y para su `compose.yaml` — y porque ninguno de los dos era JSON válido: llevaban las
  claves sin comillas, así que ese devcontainer nunca llegó a abrir.)
- ⚠️ **`devcontainer.json` es JSONC: admite comentarios, pero las claves VAN ENTRECOMILLADAS.** Un
  `name:` suelto no parsea y el devcontainer no abre. Comprobación barata:
  `sed -E 's|^\s*//.*$||' devcontainer.json | jq empty`.
- Los poms de la plantilla llevan `${groupId}`/`${artifactId}` sin resolver, así que no son poms válidos
  por sí solos: `archetype-resources/` está excluido del import de Java para que el IDE no lo marque en
  error permanente.
