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

fallos=0
ok()   { printf '  \033[0;32m✓\033[0m %s\n' "$1"; }
fail() { printf '  \033[0;31m✗\033[0m %s\n' "$1"; fallos=$((fallos+1)); }

echo "Comprobando el proyecto generado en: $GEN"

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

# ── C5 · Lo que declara skaffold existe ─────────────────────────────────────────────────────
# Fallo real: skaffold.yaml se generaba y k8s/ no (sus fileSet estaban comentados en el metadata), asi
# que el proyecto nacia apuntando a manifiestos inexistentes.
if [ -f "$GEN/skaffold.yaml" ]; then
  c5_ok=true
  while IFS= read -r m; do
    [ -e "$GEN/$m" ] || { fail "C5 skaffold.yaml referencia '$m', que no existe"; c5_ok=false; }
  done < <(grep -oE '(^|[[:space:]])- k8s/[A-Za-z0-9._/-]+' "$GEN/skaffold.yaml" | sed 's/.*- //' | grep -v '\*' | sort -u)
  $c5_ok && ok "C5 los manifiestos que declara skaffold existen"
fi

# ── C6 · Ficheros que no deberian viajar ────────────────────────────────────────────────────
for basura in __gitignore old__Dockerfile; do
  [ -e "$GEN/$basura" ] && fail "C6 '$basura' no deberia generarse" || true
done
ok "C6 sin ficheros muertos conocidos"

echo
if [ "$fallos" -eq 0 ]; then
  printf '\033[0;32m✓ %s comprobaciones estructurales OK\033[0m\n' "6"
  exit 0
fi
printf '\033[0;31m✗ %s comprobacion(es) fallidas\033[0m\n' "$fallos"
exit 1
