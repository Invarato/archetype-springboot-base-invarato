# Migraciones de base de datos (Flyway)

Cómo nace y cómo evoluciona el esquema en un microservicio generado con este arquetipo.

La decisión de usar Flyway y no Liquibase, con sus motivos, está en
[base-del-proyecto.md § D2](base-del-proyecto.md). Aquí va el **cómo**.

## La idea en una frase

**Las entidades JPA arrancan el esquema una sola vez; a partir de ahí manda Flyway.**

El objetivo del arquetipo es desarrollo rápido: escribes las entidades, sale el `V1__init.sql` solo, y ya
tienes base de datos sin haber escrito una línea de DDL. Lo que **no** hace es seguir generando: en cuanto
`V1` está commiteado, el esquema es de Flyway y las versiones siguientes se escriben a mano.

Esa frontera es lo único importante de este documento. Cruzarla al revés —regenerar el `V1` cuando ya hay
datos o cuando ya existe una `V2`— es el error caro.

## Fase 1 · Bootstrap: el `V1__init.sql` desde las entidades

Se usa la **exportación de esquema estándar de JPA** (`jakarta.persistence.schema-generation`), que trabaja
sobre el **metadato de las entidades** y por tanto **no necesita ninguna base de datos levantada**.

Perfil `ddl-dump` (solo para esto; nunca activo en dev ni en producción):

```properties
spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create
spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-source=metadata
spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=target/generated-schema/V1__init.sql
spring.jpa.properties.hibernate.hbm2ddl.delimiter=;
spring.jpa.properties.hibernate.format_sql=true

# imprescindible: sin dialecto fijado, Hibernate querria una conexion real para deducirlo
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# durante el volcado, Flyway estorba
spring.flyway.enabled=false
```

Flujo:

1. Escribes/ajustas las entidades.
2. Lanzas el volcado (irá envuelto en un objetivo del `Makefile`).
3. **Lees el SQL generado.** Este paso no es opcional (ver más abajo).
4. Lo mueves a `app/src/main/resources/db/migration/V1__init.sql`.
5. Vuelves a `ddl-auto: validate` y `spring.flyway.enabled=true`.

⚠️ **El DDL generado es un punto de partida, no un resultado.** Hibernate nombra índices y constraints como
le parece, no pone comentarios, y sus decisiones de tipos y longitudes son las genéricas. Sale un esquema que
funciona, no uno que quieras mantener diez años sin mirar. Revísalo **antes** de commitearlo, que es cuando
cuesta cero: después ya es una migración aplicada y tocarla es otra migración.

⚠️ **Solo la primera vez.** Si ya existe `V1__init.sql` en `db/migration`, el volcado **no** se vuelve a
lanzar. Regenerarlo cambiaría el fichero, y con él su **checksum**: Flyway aborta con
`Migration checksum mismatch` en cualquier entorno donde `V1` ya estuviera aplicada. Es el mecanismo
funcionando, no un fallo.

## Fase 2 · A partir de ahí: a mano

```
app/src/main/resources/db/migration/
├── V1__init.sql              generada en el bootstrap, luego intocable
├── V2__add_indice_nombre.sql
└── V3__tabla_pedidos.sql
```

Convención de Flyway: `V<version>__<descripcion>.sql`, **dos guiones bajos**. Se aplican en orden y se
registran con su checksum en `flyway_schema_history`.

**Regla de oro: una migración aplicada no se edita nunca.** Ni para arreglar un typo. Se escribe la
siguiente. El checksum existe justamente para que no puedas.

## El detector de deriva (lo que sustituye al `diff`)

Flyway Community no tiene `diff` — comparar entidades contra esquema es de pago. No hace falta, porque hay
dos mecanismos que además fallan **antes**, en el build, y no dependen de que alguien se acuerde de lanzar
nada:

**1 · `ddl-auto: validate` en todos los entornos.** Hibernate compara las entidades contra el esquema real al
arrancar y **se niega a levantar** si no cuadran. Si añades un campo a una entidad y olvidas la migración, la
aplicación no arranca. Eso es exactamente lo que queremos.

**2 · Un test de integración que corre el ciclo completo**: Testcontainers levanta un Postgres limpio, Flyway
aplica **todas** las migraciones desde cero, y el contexto de Spring arranca con `validate`. Si pasa,
entonces: las migraciones son aplicables en orden sobre una base vacía **y** el resultado concuerda con las
entidades. Es el oráculo del esquema, y cuesta un test.

⚠️ Ese test tiene que correr sobre una base **vacía**, no reutilizada: media gracia está en verificar que
`V1..Vn` se aplican en secuencia sin depender de un estado previo.

## Configuración por entorno

| | dev | test | producción |
|---|---|---|---|
| `spring.flyway.enabled` | `true` | `true` | `true` |
| `spring.jpa.hibernate.ddl-auto` | `validate` | `validate` | `validate` |
| Base de datos | `compose.yaml` | Testcontainers (efímera) | la real |

**`ddl-auto` es `validate` siempre.** No hay ningún entorno en el que Hibernate deba tocar el esquema: si
pudiera, la herramienta de migraciones sería decorativa y la deriva entre entornos aparecería sola. El único
momento en que Hibernate genera DDL es el volcado del bootstrap, y ahí no toca ninguna base de datos.

## Dependencias (Boot 4)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

⚠️ **Las dos.** Dos trampas conocidas, las dos silenciosas:

- **`flyway-core` a secas no migra nada y no avisa.** Boot 4 partió las autoconfiguraciones en starters; hace
  falta `spring-boot-starter-flyway` (G2).
- **Flyway movió el soporte de cada motor a su propio módulo.** Sin `flyway-database-postgresql`, falla al
  arrancar (G3).

Las versiones las gestiona el BOM de Spring Boot: **no las fijes a mano** (comprobado: Boot 4.1.1 gestiona
`flyway` y `flyway-database-postgresql`).

## Datos de prueba

Los datos de desarrollo **no van por Flyway**. Una migración con `INSERT`s de prueba acaba aplicándose en
producción, o dividiendo el historial por entorno — que es la forma de perder la propiedad de que
`flyway_schema_history` significa lo mismo en todas partes.

Van por un *seeder* de perfil `dev` (un `ApplicationRunner` que solo se activa con ese perfil), o por
`spring.sql.init` con `data.sql` acotado al perfil. Flyway es para **estructura**.

## Si algún día se vuelve a Liquibase

Quedó anotado en `base-del-proyecto.md` (D2 y G15): habría que usar **`liquibase-hibernate7`**, no el `6` que
traía el arquetipo, porque Boot 4.1.1 monta **Hibernate 7.4.5**. Y valorar de nuevo la licencia **FSL** de
Liquibase 5.x.
