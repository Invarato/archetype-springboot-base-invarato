#!/usr/bin/env bash
# Comprobaciones ESTRUCTURALES sobre un proyecto ya generado.
#
# Por que existe: el build del arquetipo da BUILD SUCCESS aunque produzca un proyecto roto — solo empaqueta
# ficheros de texto, no los compila. Y hay fallos que ni siquiera un `mvn verify` del proyecto generado
# detecta, porque no impiden compilar: un fichero que no se copia, una plantilla a la que Velocity se comio
# medio contenido, un JSON invalido que nadie abre.
#
# Cada comprobacion de aqui corresponde a un fallo REAL que ya nos comimos. No son hipotesis.
#
# Uso: scripts/check-generated.sh <directorio-del-proyecto-generado> [<directorio-de-la-plantilla>]

set -uo pipefail
LC_ALL=C.UTF-8; export LC_ALL

GEN="${1:?Falta el directorio del proyecto generado}"
TPL="${2:-$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/archetype-resources}"

# `pre`  = sobre los ficheros generados, sin necesidad de haberlos construido (lo normal).
# `post` = las que necesitan el proyecto YA construido (codigo generado en target/).
# El Makefile ejecuta las dos fases: `check` antes del build y `check-post` despues.
FASE="${CHECK_FASE:-pre}"

fallos=0
pasadas=0
# ⚠️ El total se CUENTA, no se escribe a mano. Antes era un literal en el mensaje final y llego a
# decir "10 comprobaciones OK" mientras una de ellas se saltaba en silencio.
ok()   { printf '  \033[0;32m✓\033[0m %s\n' "$1"; pasadas=$((pasadas+1)); }
fail() { printf '  \033[0;31m✗\033[0m %s\n' "$1"; fallos=$((fallos+1)); }

# Usado por C10 (fase pre) y por C12 (fase post).
EJEMPLO_PY="$GEN/client-python/ejemplo.py"

echo "Comprobando el proyecto generado en: $GEN (fase: $FASE)"

# Las de aqui abajo solo necesitan los ficheros generados, no el proyecto construido.
if [ "$FASE" = "pre" ]; then
# ── C1 · Placeholders sin resolver ──────────────────────────────────────────────────────────
# Fallo real: cinco ficheros de test salian con lineas que empezaban por `{groupId}.` porque en la
# plantilla les faltaba el prefijo `import $`. Compilaban en el arquetipo (es texto) y reventaban al
# generar. Un grep lo habria cazado el primer dia.
restos=$(grep -rIn --exclude-dir=target -E '\$\{(groupId|artifactId|package|version)\}|^\{groupId\}' "$GEN" 2>/dev/null || true)
if [ -z "$restos" ]; then
  ok "C1 sin placeholders sin resolver"
else
  fail "C1 quedan placeholders sin resolver:"; echo "$restos" | head -10 | sed 's/^/       /'
fi

# ── C2 · Velocity no se ha comido nada ──────────────────────────────────────────────────────
# Fallo real: MAKEFILE.md llegaba con 54 lineas menos y CERO cabeceras, y SKAFFOLD.md con 76 menos,
# porque en un fichero `filtered="true"` Velocity trata `##` como comentario de linea. El fichero existe
# y casi todo el texto sigue ahi, asi que a simple vista parece correcto. Comparar el numero de lineas
# con la plantilla es la comprobacion mas barata que lo detecta.
c2_ok=true
while IFS= read -r f; do
  rel="${f#"$TPL"/}"
  [ -f "$GEN/$rel" ] || continue
  lt=$(wc -l < "$f"); lg=$(wc -l < "$GEN/$rel")
  if [ "$lt" != "$lg" ]; then
    fail "C2 $rel: plantilla $lt lineas, generado $lg (Velocity se ha comido algo: escapa los ## como #[[##]]#)"
    c2_ok=false
  fi
done < <(find "$TPL" -name '*.md' -not -path '*/target/*' 2>/dev/null)
$c2_ok && ok "C2 los .md conservan todas sus lineas"

# ── C3 · Los devcontainer.json parsean ──────────────────────────────────────────────────────
# Fallo real: los devcontainer.json que se enviaban llevaban las claves SIN COMILLAS. No eran JSON
# valido, asi que ese devcontainer nunca llego a abrir — y no se detecto porque el fichero existe y se
# lee bien. JSONC admite comentarios; las claves siguen necesitando comillas.
c3_ok=true
while IFS= read -r f; do
  if ! sed -E 's|^[[:space:]]*//.*$||' "$f" | jq empty >/dev/null 2>&1; then
    fail "C3 ${f#"$GEN"/}: no es JSON valido (¿claves sin comillas?)"; c3_ok=false
  fi
done < <(find "$GEN" -name 'devcontainer.json' -not -path '*/target/*' 2>/dev/null)
$c3_ok && ok "C3 los devcontainer.json parsean"

# ── C4 · El wrapper funciona ────────────────────────────────────────────────────────────────
# Fallo real, por triplicado: se copiaban mvnw/mvnw.cmd pero NO .mvn/wrapper/maven-wrapper.properties
# (sin el, el wrapper no sabe que Maven bajar), el .gitignore excluia /.mvn/ entero, y encima los
# arquetipos no conservan el bit de ejecucion, asi que `./mvnw` respondia "Permission denied".
[ -x "$GEN/mvnw" ] && ok "C4a mvnw es ejecutable" || fail "C4a mvnw no tiene permiso de ejecucion"
[ -f "$GEN/.mvn/wrapper/maven-wrapper.properties" ] \
  && ok "C4b .mvn/wrapper/maven-wrapper.properties presente" \
  || fail "C4b falta .mvn/wrapper/maven-wrapper.properties (mvnw no sabria que Maven descargar)"

# ── C5 · El chart de Helm esta completo ─────────────────────────────────────────────────────
# Fallo real, y dos veces seguidas: el proyecto nacia declarando despliegues que apuntaban a ficheros
# que no se generaban. Primero un skaffold.yaml apuntando a un k8s/ que no viajaba; despues, los
# perfiles staging y prod de ese mismo skaffold apuntando a un chart de Helm que no existio nunca.
CHART=$(find "$GEN/helm" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -1)
if [ -z "$CHART" ]; then
  fail "C5 no se ha generado ningun chart en helm/"
else
  c5_ok=true
  for f in Chart.yaml values.yaml templates/deployment.yaml templates/service.yaml templates/_helpers.tpl; do
    [ -f "$CHART/$f" ] || { fail "C5 falta $f en el chart"; c5_ok=false; }
  done
  # El nombre del directorio tiene que haberse sustituido. Un `__artifactId__` literal significa que el
  # renombrado de directorios del arquetipo no funciono, y el chart seria inservible.
  case "$(basename "$CHART")" in
    *__*) fail "C5 el chart conserva un placeholder en el nombre: $(basename "$CHART")"; c5_ok=false ;;
  esac
  $c5_ok && ok "C5 el chart de Helm esta completo ($(basename "$CHART"))"
fi

# ── C7 · El contrato existe y no publica entidades ──────────────────────────────────────────
# El contrato es lo que compilan los consumidores. Dos cosas se comprueban aqui porque el build no las
# ve: que el fichero VIAJA (si no, el primer `make verify` del proyecto generado falla al no tener con
# que comparar), y que no se ha colado una entidad JPA en el.
CONTRATO="$GEN/contract/src/main/resources/openapi/openapi.json"
if [ ! -f "$CONTRATO" ]; then
  fail "C7 falta el contrato en contract/src/main/resources/openapi/openapi.json"
elif grep -qE '"(MyTable|[A-Za-z]+Entity)"[[:space:]]*:' "$CONTRATO" 2>/dev/null; then
  fail "C7 el contrato publica una ENTIDAD. Los controladores deben devolver DTOs: lo que se publica"
  echo "       acaba en todos los clientes generados y ata la API a la forma de la tabla."
else
  ok "C7 el contrato viaja y no publica entidades"
fi


# ── C9 · Los tests corren la MISMA version de Redis que el compose ──────────────────────────
# Estuvieron descuadradas (tests con redis:7-alpine, compose con redis:8.x). Es el peor descuadre
# posible: la suite da verde sobre un motor distinto del que se despliega, asi que un cambio de
# comportamiento entre versiones mayores no lo ve nadie hasta produccion.
REDIS_TEST=$(grep -rhoE 'redis:[0-9]+(\.[0-9]+)*(-[a-z]+)?' "$GEN/app/src/test/java" 2>/dev/null | sort -u)
REDIS_COMPOSE=$(grep -hoE 'redis:[0-9]+(\.[0-9]+)*(-[a-z]+)?' "$GEN/compose-app.yml" 2>/dev/null | sort -u)
if [ -z "$REDIS_TEST" ] || [ -z "$REDIS_COMPOSE" ]; then
  ok "C9 sin Redis que contrastar"
elif [ "$REDIS_TEST" = "$REDIS_COMPOSE" ]; then
  ok "C9 los tests y el compose usan $REDIS_COMPOSE"
else
  fail "C9 version de Redis descuadrada: tests '$REDIS_TEST' vs compose '$REDIS_COMPOSE'"
fi

# ── C10 · El ejemplo de Python al menos compila ─────────────────────────────────────────────
# Es codigo que se publica y que nadie ejecuta en el build: sin esto, un parentesis mal puesto viaja
# a todos los proyectos generados. No comprueba que funcione —para eso haria falta el servicio
# levantado y el paquete instalado—, solo que es Python valido.
#
# ⚠️ Se SALTA si no hay python3. El devcontainer no lo traia cuando se escribio esto, pero el runner
# de CI si, que es donde importa que no se cuele.
EJEMPLO_PY="$GEN/client-python/ejemplo.py"
if [ ! -f "$EJEMPLO_PY" ]; then
  fail "C10 falta client-python/ejemplo.py"
elif ! command -v python3 >/dev/null 2>&1; then
  ok "C10 sin python3 aqui: no se comprueba el ejemplo (CI si lo hace)"
elif python3 -m py_compile "$EJEMPLO_PY" 2>/dev/null; then
  ok "C10 el ejemplo de Python compila"
else
  fail "C10 client-python/ejemplo.py no es Python valido:"
  python3 -m py_compile "$EJEMPLO_PY" 2>&1 | sed 's/^/       /'
fi

# ── C6 · Ficheros que no deberian viajar ────────────────────────────────────────────────────
for basura in __gitignore old__Dockerfile; do
  [ -e "$GEN/$basura" ] && fail "C6 '$basura' no deberia generarse" || true
done
ok "C6 sin ficheros muertos conocidos"
fi

# ── C8 · Los clientes generan codigo de verdad ──────────────────────────────────────────────
# Un generador mal configurado no falla: no genera nada, el modulo compila vacio y el consumidor se
# encuentra un jar sin clases.
#
# ⚠️ Necesita el proyecto YA CONSTRUIDO, y ahi estuvo el fallo: `make verify` ejecuta `check` ANTES
# del build, asi que esta comprobacion se saltaba siempre en silencio — y el resumen seguia diciendo
# que estaban todas OK. Ahora las que dependen del build viven en la fase `post` y el Makefile las
# ejecuta despues. Es la misma leccion de siempre: una comprobacion que nunca se ejecuta no existe.
if [ "$FASE" = "post" ]; then
  n=$(find "$GEN/client-java/target/generated-sources" -name '*.java' 2>/dev/null | wc -l)
  [ "$n" -gt 0 ] && ok "C8 el cliente Java genera codigo ($n ficheros)" \
                 || fail "C8 el cliente Java no ha generado ninguna clase"

  # ── C11 · El cliente Python tambien genera ────────────────────────────────────────────────
  # C8 solo miraba el de Java: el modulo de Python podia estar generando cero ficheros sin que nadie
  # se enterase.
  PY_GEN="$GEN/client-python/target/generated-sources/python/api_client"
  np=$(find "$PY_GEN" -name '*.py' 2>/dev/null | wc -l)
  [ "$np" -gt 0 ] && ok "C11 el cliente Python genera codigo ($np ficheros)" \
                  || fail "C11 el cliente Python no ha generado ningun modulo"

  # ── C12 · El ejemplo de Python usa una API que EXISTE ─────────────────────────────────────
  # El equivalente de lo que hace ClienteGeneradoTest con el cliente Java, pero sin necesitar un
  # interprete de Python: se comprueba que cada metodo y cada clase que usa el ejemplo estan de
  # verdad en el cliente generado.
  #
  # Cubre el riesgo real: renombrar un metodo de un controlador cambia el operationId, y con el los
  # nombres del cliente generado (ver G31). Sin esto, el ejemplo de Python quedaria apuntando a
  # metodos que ya no existen y nadie se enteraria hasta ejecutarlo a mano.
  if [ "$np" -gt 0 ] && [ -f "$EJEMPLO_PY" ]; then
    c12_ok=true
    for metodo in $(grep -oE 'cliente\.[a-z_]+\(' "$EJEMPLO_PY" | sed 's/cliente\.//; s/($//' | sort -u); do
      grep -rq "def ${metodo}(" "$PY_GEN" 2>/dev/null \
        || { fail "C12 el ejemplo llama a '$metodo()', que no existe en el cliente generado"; c12_ok=false; }
    done
    for simbolo in $(grep -oE 'api_client\.[A-Z][A-Za-z]*' "$EJEMPLO_PY" | sed 's/api_client\.//' | sort -u); do
      grep -q "\"$simbolo\"" "$PY_GEN/__init__.py" 2>/dev/null \
        || { fail "C12 el ejemplo usa 'api_client.$simbolo', que el cliente generado no exporta"; c12_ok=false; }
    done
    $c12_ok && ok "C12 el ejemplo de Python encaja con el cliente generado"
  fi
fi

echo
if [ "$fallos" -eq 0 ]; then
  printf '\033[0;32m✓ %s comprobaciones estructurales OK (fase %s)\033[0m\n' "$pasadas" "$FASE"
  exit 0
fi
printf '\033[0;31m✗ %s comprobacion(es) fallidas\033[0m\n' "$fallos"
exit 1
