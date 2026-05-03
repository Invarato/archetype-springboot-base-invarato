# Makefile - Guía de Uso para Desarrollo de Arquetipos

Esta guía documenta el uso del Makefile para el desarrollo y testing del arquetipo Maven de Spring Boot.

## Tabla de Contenidos

- [Descripción General](#descripción-general)
- [Requisitos Previos](#requisitos-previos)
- [Inicio Rápido](#inicio-rápido)
- [Comandos Disponibles](#comandos-disponibles)
- [Variables de Configuración](#variables-de-configuración)
- [Flujos de Trabajo Comunes](#flujos-de-trabajo-comunes)
- [Detalles Técnicos](#detalles-técnicos)
- [Ejemplos Avanzados](#ejemplos-avanzados)
- [Troubleshooting](#troubleshooting)

## Descripción General

Este Makefile facilita el ciclo de desarrollo del arquetipo Maven, automatizando las siguientes tareas:

1. **Limpieza** del repositorio Maven local y directorios de proyectos generados
2. **Construcción** del arquetipo
3. **Generación** de proyectos de prueba desde el arquetipo
4. **Testing** completo del flujo de generación

## Requisitos Previos

- **Make**: Instalado en tu sistema
- **Maven**: 3.6 o superior
- **Java**: Según la versión requerida por el arquetipo
- **Bash**: Para ejecutar comandos shell (Git Bash en Windows)

## Inicio Rápido

### Ver ayuda

```bash
make help
```

### Flujo completo (recomendado)

Ejecuta todo el proceso: limpia, compila el arquetipo y genera un proyecto de prueba.

```bash
make
```

o explícitamente:

```bash
make rebuild
```

---

## Comandos Disponibles

### `make help`

Muestra la ayuda con todos los comandos disponibles y ejemplos de uso.

```bash
make help
```

**Salida:**
```
Usage: make [target] [VARIABLE=value]

Targets:
  help      Show this help message
  clean     Clean Maven repository and artifact directory
  build     Build the archetype (mvn clean install)
  generate  Generate a new project from the archetype
  rebuild   Clean, build and generate (default)
  all       Alias for rebuild
...
```

### `make clean`

Limpia los directorios necesarios para un rebuild limpio:

- **Repositorio Maven local**: Elimina la versión del arquetipo instalada en `~/.m2/repository/com/jarroba/`
- **Directorio del artefacto**: Elimina el proyecto generado previamente

```bash
make clean
```

**Ejemplo de salida:**
```
Cleaning Maven repository: /home/user/.m2/repository/com/jarroba/archetype-springboot-base-invarato/1.0.2
Cleaning artifact directory: /home/user/IdeaProjects/projectTestForAnalysis
```

### `make build`

Compila e instala el arquetipo en el repositorio Maven local.

```bash
make build
```

Ejecuta internamente:
```bash
mvn clean install -DskipTests -Dgpg.skip=true
```

### `make generate`

Genera un nuevo proyecto desde el arquetipo ya instalado.

```bash
make generate
```

**Valores por defecto:**
- `groupId`: com.jarroba
- `artifactId`: projectTestForAnalysis (o el especificado en `ARTIFACT_ID`)
- `archetypeVersion`: Extraída automáticamente del `pom.xml`

### `make rebuild`

Ejecuta el flujo completo: clean → build → generate

```bash
make rebuild
```

Este es el comando más usado durante el desarrollo del arquetipo.

### `make all`

Alias de `make rebuild`.

```bash
make all
```

---

## Variables de Configuración

Puedes personalizar el comportamiento del Makefile usando variables de entorno o parámetros en línea.

### `ARCHETYPE_VERSION`

Especifica la versión del arquetipo a usar.

**Por defecto**: Se extrae automáticamente del `pom.xml`

```bash
make rebuild ARCHETYPE_VERSION=1.0.3
```

### `ARTIFACT_ID`

Define el nombre del proyecto (artifactId) que se generará.

**Por defecto**: `projectTestForAnalysis`

```bash
make generate ARTIFACT_ID=MiProyectoPrueba
```

### Variables internas (automáticas)

Estas variables se extraen automáticamente del `pom.xml`:

- **`ARCHETYPE_GROUP_ID`**: GroupId del arquetipo
- **`ARCHETYPE_ARTIFACT_ID`**: ArtifactId del arquetipo
- **`DEFAULT_ARCHETYPE_VERSION`**: Versión del arquetipo
- **`M2_REPOSITORY_PATH`**: Ruta al repositorio Maven local (`~/.m2/repository`)
- **`PARENT_DIR`**: Directorio padre donde se generará el proyecto

---

## Flujos de Trabajo Comunes

### Desarrollo iterativo del arquetipo

Cuando estás desarrollando y modificando el arquetipo:

```bash
# 1. Hacer cambios en los archivos del arquetipo
# (modificar src/main/resources/archetype-resources/...)

# 2. Probar los cambios
make rebuild

# 3. Navegar al proyecto generado
cd ../projectTestForAnalysis

# 4. Probar que el proyecto funciona
./mvnw clean test
```

### Testing con diferentes versiones

```bash
# Probar con versión específica
make rebuild ARCHETYPE_VERSION=1.0.0

# Probar con otra versión
make rebuild ARCHETYPE_VERSION=2.0.0-SNAPSHOT
```

### Generar múltiples proyectos de prueba

```bash
# Generar primer proyecto
make generate ARTIFACT_ID=test-proyecto-1

# Generar segundo proyecto
make generate ARTIFACT_ID=test-proyecto-2

# Generar tercer proyecto
make generate ARTIFACT_ID=test-proyecto-3
```

### Limpiar y empezar de cero

```bash
# Solo limpiar
make clean

# Limpiar con variables personalizadas
make clean ARTIFACT_ID=MiProyecto ARCHETYPE_VERSION=1.5.0
```

### Solo compilar el arquetipo (sin generar)

```bash
make build
```

Útil cuando solo quieres verificar que el arquetipo compila sin errores.

---

## Detalles Técnicos

### Extracción de valores del pom.xml

El Makefile extrae automáticamente información del `pom.xml` usando comandos shell:

```makefile
ARCHETYPE_GROUP_ID := $(shell grep -m1 "<groupId>" $(POM_XML_PATH) | sed -E 's/.*<groupId>([^<]+)<\/groupId>.*/\1/')
```

Esto permite que el Makefile esté sincronizado con la configuración del proyecto.

### Directorio de generación

Los proyectos se generan en el **directorio padre** del arquetipo:

```
IdeaProjects/
├── archetype-springboot-base-invarato/    (arquetipo)
│   ├── Makefile
│   ├── pom.xml
│   └── src/
└── projectTestForAnalysis/                 (proyecto generado)
    ├── pom.xml
    └── src/
```

### Comando Maven de generación

Internamente ejecuta:

```bash
mvn -o -U archetype:generate -B \
    -DarchetypeGroupId="com.jarroba" \
    -DarchetypeArtifactId="archetype-springboot-base-invarato" \
    -DarchetypeVersion="1.0.2" \
    -DgroupId=com.jarroba \
    -DartifactId="projectTestForAnalysis" \
    -DoutputDirectory="../"
```

Parámetros:
- `-o`: Modo offline (usa cache local)
- `-U`: Fuerza actualización de snapshots
- `-B`: Modo batch (no interactivo)

---

## Ejemplos Avanzados

### Ejemplo 1: Desarrollo completo de una feature

```bash
# Día 1: Empezar desarrollo
make rebuild

# Probar el proyecto generado
cd ../projectTestForAnalysis
./mvnw spring-boot:run

# Día 2: Continuar desarrollo
cd ../archetype-springboot-base-invarato
# ... hacer cambios ...
make rebuild

# Verificar cambios en el proyecto generado
cd ../projectTestForAnalysis
./mvnw clean package
```

### Ejemplo 2: Testing de release

```bash
# Actualizar versión en pom.xml a 1.1.0

# Compilar y probar
make rebuild

# Generar proyectos de prueba con diferentes nombres
make generate ARTIFACT_ID=test-basic
make generate ARTIFACT_ID=test-full
make generate ARTIFACT_ID=test-minimal

# Verificar cada uno
cd ../test-basic && ./mvnw verify
cd ../test-full && ./mvnw verify
cd ../test-minimal && ./mvnw verify
```

### Ejemplo 3: Comparar versiones

```bash
# Probar versión actual
make rebuild ARTIFACT_ID=proyecto-v-actual

# Cambiar a versión anterior
git checkout v1.0.0

# Probar versión anterior
make rebuild ARTIFACT_ID=proyecto-v-anterior

# Comparar ambos proyectos
diff -r ../proyecto-v-actual ../proyecto-v-anterior
```

### Ejemplo 4: Limpieza selectiva

```bash
# Limpiar solo un proyecto específico
make clean ARTIFACT_ID=proyecto-viejo

# Limpiar con versión específica
make clean ARCHETYPE_VERSION=0.9.0
```

---

## Troubleshooting

### Problema: "Command not found: make"

**Solución**: Instalar make según tu sistema operativo.

```bash
# Ubuntu/Debian
sudo apt-get install make

# macOS
xcode-select --install

# Windows
# Usar Git Bash o instalar make desde GnuWin32
```

### Problema: El proyecto no se genera

**Posibles causas:**

1. **El arquetipo no está instalado en el repositorio local**
   ```bash
   make build
   ```

2. **Versión incorrecta**
   ```bash
   # Verificar versión en pom.xml
   grep -m1 "<version>" pom.xml

   # Usar versión correcta
   make generate ARCHETYPE_VERSION=1.0.2
   ```

3. **Directorio ya existe**
   ```bash
   # Limpiar primero
   make clean
   make generate
   ```

### Problema: Errores de compilación del arquetipo

**Solución**: Verificar logs de Maven

```bash
# Ejecutar build con más detalle
mvn clean install -X
```

### Problema: Variables no se extraen correctamente del pom.xml

**Solución**: Verificar formato del pom.xml

```bash
# Ver valores extraídos
make help
# Los valores se muestran en la sección "Variables"
```

### Problema: Proyectos generados aparecen en ubicación incorrecta

**Causa**: La variable `PARENT_DIR` calcula el directorio padre.

**Solución**: Verificar la estructura de directorios o especificar manualmente.

```bash
# Ver dónde se generará
echo "Parent directory: $(dirname $(pwd))"
```

---

## Integración con Scripts Bash

Este Makefile es compatible con el script bash original `restart_build.bash`.

**Equivalencias:**

```bash
# Bash script
./restart_build.bash

# Makefile
make rebuild
```

```bash
# Bash script con parámetros
./restart_build.bash -v 1.0.3 -a MiProyecto

# Makefile con parámetros
make rebuild ARCHETYPE_VERSION=1.0.3 ARTIFACT_ID=MiProyecto
```

---

## Mejores Prácticas

1. **Usar `make rebuild` por defecto** durante el desarrollo del arquetipo
2. **Usar `make clean` antes de releases** para asegurar un estado limpio
3. **Especificar `ARTIFACT_ID` único** para cada test de proyecto
4. **Verificar proyectos generados** antes de commit:
   ```bash
   make rebuild
   cd ../projectTestForAnalysis
   ./mvnw clean verify
   ```
5. **Usar variables para CI/CD**:
   ```bash
   make rebuild ARTIFACT_ID=ci-test-${BUILD_NUMBER}
   ```

---

## Referencia Rápida

```bash
# Ver ayuda
make help

# Flujo completo (más común)
make
make rebuild

# Solo limpiar
make clean

# Solo compilar arquetipo
make build

# Solo generar proyecto
make generate

# Con parámetros personalizados
make rebuild ARCHETYPE_VERSION=2.0.0 ARTIFACT_ID=MiApp

# Workflow típico
make rebuild && cd ../projectTestForAnalysis && ./mvnw verify
```

---

## Soporte

Para más información:
- Ver el código del Makefile
- Consultar `restart_build.bash` para equivalencias
- Ejecutar `make help` para ver comandos disponibles
