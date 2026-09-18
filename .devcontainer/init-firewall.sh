#!/bin/bash
# Firewall de salida (allowlist) para el devcontainer de archetype-springboot-base-invarato.
#
# QUE HACE: deja salir SOLO hacia los dominios de esta lista. Todo lo demas se descarta.
# PARA QUE: aqui dentro trabajan agentes de IA con el repo montado en lectura/escritura y con un
# token de GitHub a mano. La allowlist no impide que un agente se equivoque, pero si reduce a donde
# puede mandar algo si se equivoca (o si le cuelan instrucciones en un fichero que lee).
#
# COMO: no se resuelven los dominios "una vez y a correr" — eso no funciona. Medio internet esta
# detras de CDNs y balanceadores que devuelven un puñado de IPs distintas EN CADA CONSULTA (Docker
# Hub es el caso de libro: dos `dig` seguidos dan conjuntos casi disjuntos). Una lista fija de IPs
# acierta a veces y falla otras, que es la peor forma de fallar. Aqui el DNS del contenedor pasa por
# un dnsmasq local que mete en el ipset la IP de cada respuesta de un dominio permitido, justo antes
# de devolversela a quien pregunto. Lo que se permite es el NOMBRE; la IP la descubre el propio DNS.
# De regalo, permite subdominios: `github.com` cubre api., codeload., gist. …
#
# ⚠️ LA LISTA VIVE AQUI, y este fichero es root:root 0755 dentro de la imagen: un agente no puede
# ampliarla. Para anadir un dominio se edita este fichero y se hace "Rebuild Container" — a
# proposito: el perimetro se decide fuera de la caja, no dentro.
#
# ⚠️ LIMITE HONESTO: el contenedor es PRIVILEGIADO (lo exige Docker-in-Docker, y sin Docker no se
# pueden correr los tests del proyecto generado). Con privilegios, esto es un GUARDARRAIL, no una
# barrera: desde dentro se puede desmontar. Sirve contra el fallo dominante —el despiste, traerse algo
# de donde no toca— y no contra un agente hostil. La contencion real es la VM donde corre esto, el
# alcance del token de `gh` y la revision del diff.
set -euo pipefail
IFS=$'\n\t'

if [ "$(id -u)" -ne 0 ]; then
  echo "init-firewall.sh: hay que ejecutarlo como root (sudo /usr/local/bin/init-firewall.sh)" >&2
  exit 1
fi

# ── Allowlist ──────────────────────────────────────────────────────────────────────────────
# Sufijos de dominio: cada entrada cubre el dominio y TODOS sus subdominios.
ALLOWED_DOMAINS=(
  # Maven: dependencias y plugins del arquetipo y de los proyectos que genera
  maven.apache.org
  repo1.maven.org
  spring.io                  # milestones/snapshots de Spring, si algun dia hacen falta
  sonatype.com               # publicacion en Maven Central (portal + staging API)
  # GitHub: git sobre https, gh CLI, descargas de releases y ghcr
  github.com
  githubusercontent.com      # raw., objects., pkg-containers. …
  ghcr.io
  # Claude Code: API, login y auto-update (downloads.claude.ai sale del propio instalador)
  anthropic.com
  claude.ai
  # Registries de imagenes: los usa el Docker de dentro para bajarse
  # postgres/redis/eclipse-temurin de los tests y del compose del proyecto generado.
  docker.io
  docker.com                 # production.cloudflare.docker.com (las capas de las imagenes)
  # El servidor del IDE, que corre DENTRO del contenedor y se baja de aqui sus extensiones.
  # Sin esto, "Reopen in Container" parece funcionar pero las extensiones no se instalan — y el
  # CDN de la marketplace (*.gallerycdn.vsassets.io) es justo de los que rotan IP.
  visualstudio.com
  vsassets.io
  vscode-cdn.net
  prss.microsoft.com
  # Comprobacion de revocacion (CRL) del certificado con el que se firman las extensiones. Si se
  # descarta, la verificacion de firma de CADA extension espera ~90 s a su timeout y la carga de
  # extensiones parece colgada para siempre.
  digicert.com
  # Descomenta si trabajas con el backend de JetBrains (Gateway se lo baja dentro del contenedor):
  # jetbrains.com
)

STATE_DIR=/var/lib/cc-firewall
DNSMASQ_CONF=/etc/cc-dnsmasq.conf
DNSMASQ_PID=/run/cc-dnsmasq.pid
mkdir -p "$STATE_DIR"

# ── 1. Servidores DNS de verdad ────────────────────────────────────────────────────────────
# Se guardan la PRIMERA vez, antes de tocar resolv.conf. Sin esto, al re-ejecutar el script leeria
# su propio 127.0.0.1 como upstream y dnsmasq se preguntaria a si mismo para siempre.
if [ ! -s "$STATE_DIR/upstream" ]; then
  grep -E '^nameserver ' /etc/resolv.conf | awk '{print $2}' | grep -v '^127\.0\.0\.1$' > "$STATE_DIR/upstream" || true
fi
if [ ! -s "$STATE_DIR/upstream" ]; then
  echo "[firewall] ERROR: no encuentro ningun servidor DNS upstream en /etc/resolv.conf" >&2
  exit 1
fi

# ── 2. ipset + dnsmasq ─────────────────────────────────────────────────────────────────────
# El ipset se crea ANTES de arrancar dnsmasq: si no existe, dnsmasq no tiene donde escribir.
ipset create cc-allowed hash:ip -exist

{
  echo "# Generado por init-firewall.sh — no editar a mano"
  echo "no-resolv"          # ignora /etc/resolv.conf (que a partir de ahora apunta aqui)
  echo "no-hosts"
  echo "listen-address=127.0.0.1"
  echo "bind-interfaces"
  echo "cache-size=1000"
  echo "pid-file=$DNSMASQ_PID"
  while read -r ns; do echo "server=$ns"; done < "$STATE_DIR/upstream"
  # La linea clave: toda respuesta A de estos dominios (y sus subdominios) entra en el ipset.
  printf 'ipset=/%s/cc-allowed\n' "$(IFS=/; echo "${ALLOWED_DOMAINS[*]}")"
} > "$DNSMASQ_CONF"

# Se para el dnsmasq anterior por su pid-file (el script se re-ejecuta en CADA arranque del
# contenedor). Y se reintenta el arranque: el puerto 53 tarda un instante en quedar libre y, sin el
# bucle, la segunda ejecucion muere con "Address already in use" y te deja SIN firewall nuevo.
if [ -f "$DNSMASQ_PID" ]; then kill "$(cat "$DNSMASQ_PID")" 2>/dev/null || true; fi
pkill -f "dnsmasq -C $DNSMASQ_CONF" 2>/dev/null || true
dnsmasq_ok=false
for _ in $(seq 1 20); do
  if dnsmasq -C "$DNSMASQ_CONF" 2>/dev/null; then dnsmasq_ok=true; break; fi
  sleep 0.5
done
if [ "$dnsmasq_ok" != true ]; then
  echo "[firewall] ERROR: dnsmasq no arranca — sin el, la allowlist no se puede poblar" >&2
  exit 1
fi

# Todo el contenedor resuelve ahora por dnsmasq. /etc/resolv.conf es un bind mount de Docker: se
# puede escribir dentro, pero NO reemplazar (por eso `>` y no `mv`).
printf 'nameserver 127.0.0.1\noptions timeout:2 attempts:3\n' > /etc/resolv.conf

# ── 3. Reglas ──────────────────────────────────────────────────────────────────────────────
# Chains propias en vez de tocar las policies por defecto ni hacer `iptables -F`: cuando esto se
# ejecuta, dockerd YA ha creado sus cadenas, y un flush le deja a los contenedores de dentro sin red
# (un fallo que luego parece "Testcontainers no arranca").
for chain in CC-IN CC-OUT; do
  iptables -N "$chain" 2>/dev/null || iptables -F "$chain"
done

# Salida
iptables -A CC-OUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
iptables -A CC-OUT -o lo -j ACCEPT
# Redes puente de Docker: la JVM hablando con los contenedores de Testcontainers, y los
# contenedores entre si. Es trafico local a la maquina, no salida a internet.
iptables -A CC-OUT -o docker0 -j ACCEPT
iptables -A CC-OUT -o br+ -j ACCEPT
# DNS: SOLO hacia los upstream reales, y solo lo emite dnsmasq. Un proceso que intente hablar por
# su cuenta con 8.8.8.8 para saltarse el resolver se queda fuera.
# ⚠️ Lo que esto NO cierra: dnsmasq reenvia cualquier nombre que se le pida, asi que el puerto 53
# sigue siendo un canal de fuga lento (DNS tunneling). Cerrarlo del todo pide un resolver que
# ademas filtre por nombre; asumido y sabido.
while read -r ns; do
  iptables -A CC-OUT -p udp -d "$ns" --dport 53 -j ACCEPT
  iptables -A CC-OUT -p tcp -d "$ns" --dport 53 -j ACCEPT
done < "$STATE_DIR/upstream"
iptables -A CC-OUT -m set --match-set cc-allowed dst -j ACCEPT
iptables -A CC-OUT -j DROP

# Entrada
iptables -A CC-IN -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
iptables -A CC-IN -i lo -j ACCEPT
iptables -A CC-IN -i docker0 -j ACCEPT
iptables -A CC-IN -i br+ -j ACCEPT
iptables -A CC-IN -j DROP

# Enganche. En la posicion 1 y con DROP al final de la cadena: lo que no encaje muere ahi, sin
# necesidad de cambiar la policy (que es de dockerd tanto como nuestra).
iptables -C OUTPUT -j CC-OUT 2>/dev/null || iptables -I OUTPUT 1 -j CC-OUT
iptables -C INPUT  -j CC-IN  2>/dev/null || iptables -I INPUT  1 -j CC-IN

# Trafico de los contenedores de dentro hacia fuera. DOCKER-USER es la cadena
# que dockerd deja libre para reglas de usuario y que evalua ANTES que las suyas; solo existe si
# dockerd esta arriba.
if iptables -L DOCKER-USER -n >/dev/null 2>&1; then
  iptables -F DOCKER-USER
  iptables -A DOCKER-USER -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
  iptables -A DOCKER-USER -i br+ -o br+ -j ACCEPT
  iptables -A DOCKER-USER -i docker0 -o docker0 -j ACCEPT
  iptables -A DOCKER-USER -p udp --dport 53 -j ACCEPT
  iptables -A DOCKER-USER -m set --match-set cc-allowed dst -j ACCEPT
  iptables -A DOCKER-USER -j DROP
  echo "[firewall] DOCKER-USER configurada (los contenedores de dentro salen con la misma lista)"
fi

# ── 4. Verificacion ────────────────────────────────────────────────────────────────────────
# Sin esto no sabrias si el firewall esta puesto o solo lo parece. Falla ruidosamente, porque un
# firewall que se cree activo y no lo esta es peor que no tenerlo: da confianza sin darla.
echo "[firewall] verificando..."
if curl -s --max-time 8 https://example.com >/dev/null 2>&1; then
  echo "[firewall] ERROR: example.com deberia estar BLOQUEADO y responde" >&2
  exit 1
fi
if ! curl -s --max-time 20 -o /dev/null https://repo1.maven.org/maven2/ ; then
  echo "[firewall] ERROR: Maven Central deberia estar PERMITIDO y no responde" >&2
  exit 1
fi
echo "[firewall] OK — salida restringida a ${#ALLOWED_DOMAINS[@]} dominios (y sus subdominios)"
