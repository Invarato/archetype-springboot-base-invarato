# Devcontainer del repo archetype-springboot-base-invarato.
#
# Este repo NO es una app Spring Boot: es el ARQUETIPO Maven que las genera (packaging
# `maven-archetype`). Por eso aqui no hay Postgres ni Redis ni MinIO — el proyecto generado
# se trae su propio compose.
#
# PERFIL UNICO, con Docker-in-Docker: sin Docker no se pueden correr los tests de integracion del
# proyecto generado, que es lo unico que comprueba de verdad si el arquetipo funciona. El porque
# completo (y que se pierde con ello) esta en devcontainer.json y en el README.
#
# Base bookworm pelada + el feature `java` (JDK 25 + Maven) en devcontainer.json, en vez de la
# imagen `devcontainers/java:25`: asi el JDK y Maven quedan declarados y versionados en un solo
# sitio, junto al resto de features. Usuario del contenedor: vscode.
FROM mcr.microsoft.com/devcontainers/base:bookworm

# Zona horaria (se pasa como build arg desde devcontainer.json)
ARG TZ=Europe/Madrid
ENV TZ=${TZ}

# make: el flujo de trabajo de este repo ES el Makefile (`make rebuild`), y la imagen base no lo trae.
# gnupg: firmar los artefactos al publicar en Maven Central (maven-gpg-plugin). El dia a dia va con
#        -Dgpg.skip=true, pero sin gnupg `mvn central-publishing:publish` no arranca.
# iptables/ipset/dnsmasq/dnsutils/jq: los usa init-firewall.sh (allowlist de salida). Van en la imagen
#        y no se instalan en caliente a proposito: dentro corren agentes, y el firewall no puede
#        depender de que haya red para poder levantarse.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      make gnupg iptables ipset dnsmasq dnsutils jq \
 && rm -rf /var/lib/apt/lists/*

# ── Sobre el sudo ──────────────────────────────────────────────────────────────────────────
# `vscode` conserva el sudo NOPASSWD completo que trae la imagen base. Aqui hubo un mecanismo para
# recortarlo a un unico permiso (lanzar el firewall), y se ha QUITADO a proposito: Docker-in-Docker
# exige `privileged`, y en un contenedor privilegiado restringir sudo es teatro — se es root de todas
# formas por otras vias. Un candado que no cierra pero estorba (no puedes `apt-get install`) es peor
# que no tener candado, porque invita a confiar en el.
#
# La contencion real de este entorno NO es el contenedor: es la maquina/VM donde corre, el alcance del
# token de `gh` y la revision del diff. El firewall de salida se mantiene porque si detiene el fallo
# dominante, que es el despiste.

COPY init-firewall.sh /usr/local/bin/init-firewall.sh
# Root-owned y 0755: la allowlist vive DENTRO del script, asi que un agente no puede ampliarla sin
# pasar por un rebuild de la imagen. Si el script fuera editable desde dentro, el firewall seria
# decorativo.
RUN chown root:root /usr/local/bin/init-firewall.sh && chmod 0755 /usr/local/bin/init-firewall.sh

# ⚠️ IMPRESCINDIBLE: crear estos directorios AQUI, antes de que se monten los volumenes encima.
# Docker solo hereda el ownership de un volumen nuevo si la ruta YA EXISTE en la imagen; si no
# existe, la crea root:root y el usuario `vscode` (uid 1000) no puede escribir → Maven no puede
# usar ~/.m2 y Claude Code no arranca dentro del contenedor.
#
# /workspace se crea aparte y por un motivo distinto: el repo se monta en /workspace/<repo> y
# `make generate` escribe el proyecto de prueba en el directorio PADRE, o sea en /workspace.
# Tiene que existir y ser escribible por vscode o la generacion falla con un permiso denegado.
#
# ~/worktrees: donde los agentes crean sus git worktrees. FUERA del repo a proposito — dentro, el
# IDE indexaria varias copias del mismo proyecto y cualquier busqueda devolveria todo duplicado.
RUN mkdir -p /home/vscode/.m2 /home/vscode/.claude /home/vscode/.config/gh /home/vscode/.gnupg \
             /home/vscode/.ssh /home/vscode/.docker /home/vscode/.commandhistory \
             /home/vscode/worktrees /workspace \
 && touch /home/vscode/.commandhistory/.bash_history /home/vscode/.commandhistory/.zsh_history \
 && chown -R vscode:vscode /home/vscode/.m2 /home/vscode/.claude /home/vscode/.config \
             /home/vscode/.gnupg /home/vscode/.ssh /home/vscode/.docker \
             /home/vscode/.commandhistory /home/vscode/worktrees /workspace \
 && chmod 700 /home/vscode/.gnupg /home/vscode/.ssh
# ⚠️ .gnupg y .ssh en 700, y las claves dentro en 600, o las herramientas los IGNORAN en silencio:
# gpg dice "unsafe permissions" justo al firmar la release, y ssh descarta la clave y acaba pidiendo
# CONTRASENA — un sintoma que no apunta por ningun lado a los permisos.

# Historial de shell en un directorio que es volumen. Sin esto se pierde en cada rebuild, que es de
# las cosas que mas se echan de menos y menos se piensan al montar volumenes: el comando largo de
# ayer no esta y no hay forma de recuperarlo. Se cablea para bash Y para zsh (el usuario por defecto
# entra con bash, pero la imagen trae zsh y mucha gente se cambia).
RUN echo 'export HISTFILE=/home/vscode/.commandhistory/.bash_history' >> /home/vscode/.bashrc \
 && echo "export PROMPT_COMMAND='history -a'" >> /home/vscode/.bashrc \
 && echo 'export HISTFILE=/home/vscode/.commandhistory/.zsh_history' >> /home/vscode/.zshrc \
 && echo 'setopt INC_APPEND_HISTORY' >> /home/vscode/.zshrc

# Que git use el token de `gh` para GitHub. Va en la config de SISTEMA y no en ~/.gitconfig (que es
# lo que escribe `gh auth setup-git`) porque $HOME no es volumen: si no, habria que repetirlo en
# cada rebuild.
#
# ⚠️ Esto arregla un fallo que despista mucho: `gh auth status` dice que estas autenticado y aun asi
# `git push` responde «403: Write access to repository not granted». No es el token — es que git no
# lo estaba usando, porque tira del credential helper del IDE, que es otro.
RUN git config --system credential."https://github.com".helper '!/usr/bin/gh auth git-credential' \
 && git config --system credential."https://gist.github.com".helper '!/usr/bin/gh auth git-credential'

# ── Claude Code ────────────────────────────────────────────────────────────────────────────
# CLAUDE_CONFIG_DIR se declara ANTES de instalar y apunta al directorio que es volumen. Es la pieza
# que hace que la sesion sobreviva a un rebuild: por defecto Claude Code guarda su estado en DOS
# sitios, ~/.claude (transcripciones, credenciales, settings) y el fichero ~/.claude.json (cuenta,
# historial por proyecto, MCP) — y ese fichero queda FUERA de un volumen montado en ~/.claude, asi
# que se perderia en cada rebuild. Un symlink no vale: el fichero se reescribe con temp+rename y el
# enlace acaba sustituido por un fichero normal. Con esta variable, .claude.json se escribe dentro
# del directorio y entra en el volumen. (Verificado con esta imagen.)
ENV CLAUDE_CONFIG_DIR=/home/vscode/.claude

# Instalado por su INSTALADOR NATIVO (binario), no por la feature `claude-code`: esa instala via
# npm y, si no encuentra Node, se trae Node 18 (EOL) de nodesource. Este repo es JVM puro — no
# queremos un runtime de Node aqui solo para la herramienta.
USER vscode
RUN curl -fsSL https://claude.ai/install.sh | bash
USER root
ENV PATH="/home/vscode/.local/bin:${PATH}"
