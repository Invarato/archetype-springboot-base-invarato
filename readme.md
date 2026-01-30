# Maven Archetype: Spring Boot Base (Invarato)

[![Maven Repository](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fcom%2Fjarroba%2Farchetype-springboot-base-invarato%2Fmaven-metadata.xml&style=flat-square)](https://mvnrepository.com/artifact/com.jarroba/archetype-springboot-base-invarato)

[![Maven Central](https://img.shields.io/badge/Maven%20Central-1.0.2-blue?style=flat-square)](https://central.sonatype.org/artifact/com.jarroba/archetype-springboot-base-invarato)

Arquetipo de Maven para crear proyectos base de Spring Boot listos para producción, con capas de controlador/servicio/repositorio, mapeos, manejo global de excepciones, integración con Liquibase, pruebas unitarias e integración con Testcontainers y configuración Docker/Compose opcional.

Repositorio: https://github.com/Invarato/archetype-springboot-base-invarato


## Requisitos mínimos
- JDK 25
- Maven 3.6.3+
- Docker 27+ (solo si vas a usar Testcontainers con Docker o levantar servicios con Compose)


## Cómo usar el arquetipo
Genera un proyecto nuevo a partir del arquetipo publicado:

```shell
mvn archetype:generate \
  -DarchetypeGroupId=com.jarroba \
  -DarchetypeArtifactId=archetype-springboot-base-invarato \
  -DarchetypeVersion=<version-publicada> \
  -DgroupId=<tu.grupo> \
  -DartifactId=<tu-artifactId>
```

Parámetros principales:
- archetypeGroupId: com.jarroba
- archetypeArtifactId: archetype-springboot-base-invarato
- archetypeVersion: versión publicada en Maven Central (consulta el portal)
- groupId: grupo para tu nuevo proyecto
- artifactId: nombre de tu nuevo proyecto

Ejemplo local (SNAPSHOT):
```shell
# Limpia repositorio local de SNAPSHOT, recompila el arquetipo e instálalo
rm -r ~/.m2/repository/com/jarroba/archetype-springboot-base-invarato/1.0.0-SNAPSHOT || true
mvn clean install

# Genera un proyecto desde el SNAPSHOT local
mvn archetype:generate \
  -DarchetypeGroupId=com.jarroba \
  -DarchetypeArtifactId=archetype-springboot-base-invarato \
  -DarchetypeVersion=1.0.0-SNAPSHOT \
  -DgroupId=mi.dominio \
  -DartifactId=mi-proyecto
```


## Estructura del arquetipo
El arquetipo empaqueta una plantilla de proyecto Spring Boot con:
- Controladores de ejemplo e interceptores de errores
- Servicios, repositorios y entidades base
- DTOs y mapeadores con MapStruct
- Migraciones con Liquibase
- Tests unitarios e integración (JUnit + Testcontainers)
- Dockerfile y archivos docker-compose opcionales

Al generar el proyecto final, todo el contenido de la plantilla sustituye variables Maven (por ejemplo ${groupId}, ${artifactId}) y queda como un proyecto normal, independiente del arquetipo.


## Notas sobre los readme de la plantilla
Dentro de la carpeta del arquetipo encontrarás un readme.md de la PLANTILLA con cabeceras escapadas para que Maven no elimine caracteres durante la generación. Verás marcas como:
- #[[#]]# ${artifactId}
- #[[##]]# Sección

Es totalmente intencional. Cuando generes el proyecto final, esas marcas se transforman en cabeceras Markdown correctas (H1/H2) conservando los placeholders ya sustituidos.


## Publicar en Maven Central (para mantenedores)
Este proyecto usa el Central Publishing Plugin de Sonatype. Pasos resumidos:

1) Genera tu token en el portal
- https://central.sonatype.org/publish/generate-portal-token/

2) Configura tus credenciales en ~/.m2/settings.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>central</id>
      <username>your-sonatype-username</username>
      <password>your-portal-token</password>
    </server>
  </servers>


  <profiles>
      <profile>
          <id>ossrh</id>
          <properties>
              <gpg.executable>gpg</gpg.executable>
              <!-- Contrasena GPG con la que firmar los artefactos -->
              <gpg.passphrase>SU_CONTRASEÑA_GPG</gpg.passphrase>
          </properties>
      </profile>
  </profiles>
    
    
    
</settings>
```

3) Crear claves GPG (con linux)
https://central.sonatype.org/publish/requirements/gpg/
````shell
gpg --version
# Generating a Key Pair
gpg --gen-key
# Listar claves para obtener el ID.
gpg --list-keys
# Ejemplo de salida:
# pub   rsa3072 2024-02-13 [SC] [expires: 2026-02-12]
#       AAAA1111BBBB2222CCCC3333DDDD4444EEEE5555  # Este es el ID completo
# uid           [ultimate] Su Nombre <su.email@ejemplo.com>
# sub   rsa3072 2024-02-13 [E] [expires: 2026-02-12]

# Enviar clave a keyserver.ubuntu.com

# Enviar clave los servidores
#gpg --keyserver keyserver.ubuntu.com --send-keys AAAA1111BBBB2222CCCC3333DDDD4444EEEE5555
#gpg --keyserver keys.openpgp.org --send-keys AAAA1111BBBB2222CCCC3333DDDD4444EEEE5555
#gpg --keyserver pgp.mit.edu --send-keys AAAA1111BBBB2222CCCC3333DDDD4444EEEE5555

# O Enviar clave a varios keyservers compatibles
KEY_ID="AAAA1111BBBB2222CCCC3333DDDD4444EEEE5555" && for server in keyserver.ubuntu.com keys.openpgp.org pgp.mit.edu; do gpg --keyserver "$server" --send-keys "$KEY_ID"; done

````


4) Compila y publica (con linux). Si pide contraseña es la que configuraste en ~/.m2/settings.xml en <gpg.passphrase>
```shell
#mvn clean install
#mvn central-publishing:publish
#mvn clean install central-publishing:publish
#mvn clean install central-publishing:publish -Darchetype.jar.included
mvn clean install central-publishing:publish
```

5) Descargar
Última versión:
   [![Latest Version](https://img.shields.io/badge/Latest-1.0.2-blue?style=flat-square)](https://mvnrepository.com/artifact/com.jarroba/archetype-springboot-base-invarato)

Con comando
````shell
mvn archetype:generate \
  -DarchetypeGroupId=com.jarroba \
  -DarchetypeArtifactId=archetype-springboot-base-invarato \
  -DarchetypeVersion=1.0.2 \
  -DgroupId=com.ejemplo \
  -DartifactId=mi-proyecto \
  -Dversion=1.0.0
````

Desde web busque `com.jarroba archetype-springboot-base-invarato` en:
 * Maven Central (propagación inmediata): https://search.maven.org/
 * MVNRepository (tarda en propagar): https://mvnrepository.com/
 * Maven Repository Search (tarda en propagar): https://repository.sonatype.org/


Notas:
- (No logrado) No es necesario firmar artefactos con GPG cuando usas el portal de Sonatype con el plugin central-publishing.
- Asegúrate de que el POM contenga: name, description, url, licenses, developers y scm (ya configurados).


## Contribuir
- Issues y PRs bienvenidos. Sigue la estructura existente y añade pruebas cuando modifiques el arquetipo.

## Licencia
- MIT License. Ver sección de licencias en el POM o el archivo LICENSE del repositorio.