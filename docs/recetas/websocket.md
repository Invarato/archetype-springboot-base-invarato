# Receta · WebSocket

Un endpoint WebSocket con eco, autenticado con el mismo JWT que el resto de la API.

> **Verificado con** arquetipo 2.0.0 · Spring Boot 4.1.1 · `spring-boot-starter-websocket` · 2026-09-10.

## Qué cuesta

- **El contrato deja de contarlo todo.** OpenAPI describe peticiones y respuestas HTTP; un WebSocket no
  aparece. Los clientes generados —Java y Python— **no sabrán que existe**, así que el protocolo de los
  mensajes lo tendrás que documentar y versionar aparte. Es la pérdida más importante de esta receta.
- **Conexiones largas en un servicio pensado como stateless.** Cada conexión ocupa un hilo o un
  descriptor durante minutos u horas, y al escalar aparece el problema de siempre: el cliente está pegado
  a *una* réplica. Repartir mensajes entre réplicas ya pide un broker detrás.
- **Los despliegues cortan.** Cada actualización tira todas las conexiones. El cliente tiene que saber
  reconectar; si no lo hace, el corte se nota.

Si lo que quieres es «que el servidor avise al navegador», mira antes **SSE**
(`text/event-stream`): es unidireccional, va sobre HTTP normal, lo cubre la seguridad tal cual y no
necesita nada de esto. WebSocket vale la pena cuando el tráfico va **en los dos sentidos**.

## 1 · Dependencia

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

## 2 · El manejador

```java
package com.ejemplo.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@Slf4j
public class EchoWebSocketHandler extends TextWebSocketHandler {

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Conectado {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.sendMessage(new TextMessage("eco: " + message.getPayload()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Desconectado {} ({})", session.getId(), status);
    }
}
```

⚠️ **El manejador es un singleton compartido por todas las conexiones.** No guardes estado de una sesión
en un campo de esta clase: se mezclaría entre clientes. Lo que sea de una conexión va en los
`session.getAttributes()`, o en un mapa indexado por `session.getId()`.

## 3 · Registrarlo

```java
package com.ejemplo.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String RUTA = "/ws/echo";

    private final EchoWebSocketHandler echoWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echoWebSocketHandler, RUTA);
    }
}
```

## 4 · Seguridad: no hay que tocar nada (y eso es la noticia)

Lo primero que hizo el endpoint recién montado fue **rechazar la conexión**:

```
jakarta.websocket.DeploymentException: Failed to handle HTTP response code [401].
Unsupported Authentication scheme [Bearer] returned in response
```

Es correcto y conviene entender por qué: el handshake de un WebSocket es **una petición HTTP GET**
normal, así que pasa por la misma cadena de seguridad que el resto de la API. Como el arquetipo exige
autenticación en todo lo que no sean las sondas, el handshake sin token se queda fuera.

**Con un cliente que no sea un navegador** —otro servicio, un test, una app móvil— no hay nada que
cambiar: se manda la cabecera `Authorization: Bearer …` en el handshake y funciona.

**Desde un navegador sí hay un problema real, y es de la API del navegador, no de Spring:** el
constructor `new WebSocket(url)` **no permite añadir cabeceras**. Las salidas habituales:

| Opción | Qué implica |
|---|---|
| Token en el **subprotocolo** (`Sec-WebSocket-Protocol`) | Es lo recomendable. El navegador sí lo permite: `new WebSocket(url, ["bearer", token])`. Pide un `BearerTokenResolver` propio que lo lea de esa cabecera. |
| Token en la **query string** (`?access_token=…`) | Lo más rápido y lo peor: las URLs acaban en logs de acceso, en el historial y en las cabeceras `Referer`. Un token en un log es un token filtrado. |
| **Cookie** de sesión | Obliga a tener sesión, justo lo que el arquetipo evita a propósito ([D7](../base-del-proyecto.md)). |

## 5 · El test

Comprueba el viaje completo: conecta, manda y recibe.

```java
package com.ejemplo.websocket;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// RANDOM_PORT y no MockMvc: un WebSocket necesita un servidor de verdad escuchando en un socket.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIT {

    @LocalServerPort
    private int puerto;

    @Value("${app.dev.jwt-secret}")
    private String secreto;

    // Se firma con el mismo secreto HS256 que usa configs/DevJwtConfig en los perfiles dev y test.
    private String token() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject("test-ws")
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .build());
        jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @Test
    void haceEcoConTokenValido() throws Exception {
        BlockingQueue<String> recibidos = new LinkedBlockingQueue<>();
        WebSocketHttpHeaders cabeceras = new WebSocketHttpHeaders();
        cabeceras.setBearerAuth(token());

        WebSocketSession sesion = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                        recibidos.add(m.getPayload());
                    }
                }, cabeceras, URI.create("ws://localhost:" + puerto + WebSocketConfig.RUTA))
                .get(10, TimeUnit.SECONDS);

        sesion.sendMessage(new TextMessage("hola"));
        String respuesta = recibidos.poll(10, TimeUnit.SECONDS);
        sesion.close();

        assertNotNull(respuesta, "No hubo respuesta del WebSocket");
        assertEquals("eco: hola", respuesta);
    }
}
```

```shell
./mvnw verify -Dit.test=WebSocketIT
```

Quita el `cabeceras.setBearerAuth(...)` y el test debe fallar con un 401. Merece la pena comprobarlo una
vez: es lo que confirma que el endpoint está protegido de verdad y no solo cuando tú te acuerdas.

## Tropiezos

**`@LocalServerPort` cambió de paquete en Spring Boot 4**: ahora es
`org.springframework.boot.test.web.server.LocalServerPort`. El del sitio de antes no existe y el error
solo dice que el paquete no existe.

**Espera con `poll(timeout)`, nunca con `Thread.sleep`.** Un WebSocket es asíncrono por definición: un
sleep fijo o alarga cada ejecución sin motivo, o se queda corto en una máquina cargada y el test se
vuelve intermitente.
