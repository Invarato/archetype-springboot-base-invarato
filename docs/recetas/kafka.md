# Receta · Kafka

Publicar y consumir eventos, con un test contra un broker real.

> **Verificado con** arquetipo 2.0.0 · Spring Boot 4.1.1 · `spring-boot-starter-kafka` · Kafka 4.2.1 ·
> Testcontainers 2.0.5 · 2026-09-10.

## Qué cuesta

Antes de empezar, para que la decisión sea con los ojos abiertos:

- **Un contenedor más en cada build.** El test de esta receta levanta un broker.
- **Ruido en tests que no tienen nada que ver.** Sin el paso 5, los listeners intentan conectarse en
  cada `@SpringBootTest`. Medido: **14 errores** de conexión contra `localhost:9092` en una suite donde
  solo un test usa Kafka.
- **Un sistema más que puede estar caído** y del que ahora depende tu arranque.

Si lo que necesitas es «avisar a otra parte de mi propia aplicación», los eventos de Spring
(`ApplicationEventPublisher`) hacen eso sin infraestructura. Kafka empieza a valer cuando el que escucha
es **otro servicio**, o cuando quieres poder releer lo ya publicado.

## 1 · Dependencias

En `app/pom.xml`. Sin versión: las gobierna el BOM de Spring Boot.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-kafka</artifactId>
    <scope>test</scope>
</dependency>
```

## 2 · Configuración

En `app/src/main/resources/application.yaml`, dentro de `spring:`:

```yaml
  # === KAFKA ===
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: ${spring.application.name}
      auto-offset-reset: earliest
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.ejemplo.*
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

Tres decisiones que conviene entender, porque las tres muerden:

- **`trusted.packages`.** El `JsonDeserializer` reconstruye la clase que le diga una cabecera del
  mensaje, así que sin lista blanca **quien pueda escribir en el topic elige qué clase instancias en tu
  proceso**. Es la misma familia de fallos que en la caché (G36). No pongas `*`.
- **`auto-offset-reset: earliest`.** Con el valor por defecto (`latest`), un consumidor que se incorpora
  después de que se publicara el mensaje **no lo ve nunca**. En tests eso es una carrera que a veces pasa
  y a veces no.
- **`group-id`.** Todos los consumidores del mismo grupo se reparten los mensajes; los de grupos
  distintos reciben cada uno una copia. Si dos réplicas de tu servicio comparten grupo, cada mensaje lo
  procesa una — que suele ser lo que quieres.

## 3 · El evento y quien lo publica

```java
package com.ejemplo.messaging;

import java.time.Instant;

public record ExampleEvent(Long id, String name, Instant occurredAt) {
}
```

```java
package com.ejemplo.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExampleEventPublisher {

    public static final String TOPIC = "examples";

    private final KafkaTemplate<String, ExampleEvent> kafkaTemplate;

    public void publish(ExampleEvent event) {
        // La clave decide la particion: los eventos de un mismo id van siempre a la misma y conservan
        // su orden entre ellos. Sin clave se reparten a voleo y el orden se pierde.
        kafkaTemplate.send(TOPIC, String.valueOf(event.id()), event);
    }
}
```

## 4 · Quien lo consume

```java
package com.ejemplo.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ExampleEventListener {

    @KafkaListener(topics = ExampleEventPublisher.TOPIC)
    public void onExampleEvent(ExampleEvent event) {
        log.info("Recibido {}", event);
    }
}
```

⚠️ **Si este método lanza una excepción, el mensaje se reintenta.** Por defecto, indefinidamente: un
mensaje que siempre falla bloquea su partición para siempre. En cuanto esto pase de ejemplo a producción,
configura una *dead letter topic* — no es opcional.

## 5 · Que no moleste al resto de los tests

Este paso es el que nadie da hasta que ve el log lleno de errores rojos en tests que no tocan Kafka.

En `app/src/test/resources/application-test.yaml`:

```yaml
  kafka:
    listener:
      auto-startup: false
```

Y el test de Kafka los vuelve a encender solo para él:

```java
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=true")
```

Medido en una suite real: **14 errores de conexión a `localhost:9092` → 0**.

## 6 · El test

```java
package com.ejemplo.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=true")
@ActiveProfiles("test")
@Testcontainers
@Import(KafkaIT.Escucha.class)
// Ver «El contexto que sobrevive al contenedor», mas abajo.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.1"));

    @DynamicPropertySource
    static void propiedadesDeKafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    // Un consumidor propio del test, en OTRO grupo: recibe su copia sin quitarsela al de la aplicacion.
    @TestConfiguration
    static class Escucha {
        final BlockingQueue<ExampleEvent> recibidos = new LinkedBlockingQueue<>();

        @KafkaListener(topics = ExampleEventPublisher.TOPIC, groupId = "test-espia")
        void recibir(ExampleEvent evento) {
            recibidos.add(evento);
        }
    }

    @Autowired
    private ExampleEventPublisher publisher;

    @Autowired
    private Escucha escucha;

    @Test
    void publicaYSeRecibe() throws Exception {
        publisher.publish(new ExampleEvent(1L, "Uno", Instant.now()));

        // Con espera, no con Thread.sleep: el mensaje tarda lo que tarde, y un sleep fijo o alarga el
        // test sin motivo o lo vuelve intermitente en una maquina cargada.
        ExampleEvent recibido = escucha.recibidos.poll(20, TimeUnit.SECONDS);

        assertNotNull(recibido, "No llego el evento");
        assertEquals("Uno", recibido.name());
    }
}
```

```shell
./mvnw verify -Dit.test=KafkaIT
```

## Tropiezos

**La imagen es `apache/kafka`, no la de Confluent.** Testcontainers 2.x trae
`org.testcontainers.kafka.KafkaContainer`, que usa la imagen oficial en modo KRaft — sin Zookeeper.
`ConfluentKafkaContainer` sigue existiendo, para la otra.

**El contexto que sobrevive al contenedor.** Al terminar `KafkaIT`, Testcontainers para el broker, pero
Spring **guarda el contexto en su caché** para reutilizarlo: los consumidores siguen vivos reintentando
contra un puerto que ya no existe, y el final de la suite se llena de errores. Medido: **285 errores de
red** por esto. Con `@DirtiesContext(classMode = AFTER_CLASS)` el contexto se cierra con el contenedor y
bajan a **0**.

**No inventes cabeceras de tipo a mano.** El `JsonSerializer` añade una cabecera con la clase; el
deserializador la usa. Si publicas desde otro sistema que no la pone, configura el tipo por defecto en el
consumidor en vez de intentar reproducir la cabecera.
