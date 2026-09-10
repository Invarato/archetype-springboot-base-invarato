# Receta · gRPC

Un servicio gRPC generado desde un `.proto`, protegido con el mismo JWT que la API REST.

> **Verificado con** arquetipo 2.0.0 · Spring Boot 4.1.1 · `spring-boot-starter-grpc-server` ·
> Spring gRPC 1.1.1 · grpc-java 1.83.1 · protobuf 4.35.1 · 2026-09-10.

## Qué cuesta

- **Un segundo puerto.** gRPC no viaja por el mismo puerto que el HTTP: hay que abrirlo, publicarlo en el
  `Service` de Kubernetes y sondearlo aparte. Las sondas HTTP del arquetipo **no lo cubren**.
- **Una cadena de herramientas nueva.** `protoc` y su plugin de Java se descargan y ejecutan en cada
  build. Es rápido y automático, pero es una pieza más que puede fallar en CI.
- **Dos contratos que mantener.** El `.proto` es un contrato tan público como el OpenAPI, con sus propias
  reglas de compatibilidad — nunca reutilices un número de campo, nunca cambies el tipo de uno existente.

**Cuándo compensa de verdad:** comunicación entre servicios internos con mucho volumen o baja latencia,
o streaming bidireccional. Para una API que consumen navegadores o terceros, REST + OpenAPI sigue ganando
— y es lo que el arquetipo ya te da hecho, con clientes generados incluidos.

## 1 · Dependencias

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-grpc-server</artifactId>
</dependency>
<!-- Solo si además llamas a otros servicios por gRPC (y para el test de esta receta). -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-grpc-client</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-grpc-server-test</artifactId>
    <scope>test</scope>
</dependency>
```

## 2 · El plugin que genera el código

En `app/pom.xml`, dentro de `<build><plugins>`:

```xml
<plugin>
    <groupId>io.github.ascopes</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <configuration>
        <protocVersion>${protobuf-java.version}</protocVersion>
        <binaryMavenPlugins>
            <binaryMavenPlugin>
                <groupId>io.grpc</groupId>
                <artifactId>protoc-gen-grpc-java</artifactId>
                <version>${grpc-java.version}</version>
                <options>jakarta_omit,@generated=omit</options>
            </binaryMavenPlugin>
        </binaryMavenPlugins>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Ni el plugin ni `protoc` llevan versión escrita a mano: **el BOM de Spring Boot gestiona las tres**
(`protobuf-maven-plugin.version`, `protobuf-java.version`, `grpc-java.version`). Fijarlas por tu cuenta
es la forma habitual de acabar con un `protoc` que no case con la biblioteca de runtime.

## 3 · El contrato

`app/src/main/proto/example.proto`:

```proto
syntax = "proto3";

package example;

option java_multiple_files = true;
option java_package = "com.ejemplo.grpc.proto";

service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply) {}
}

message HelloRequest {
  string name = 1;
}

message HelloReply {
  string message = 1;
}
```

Los números de campo (`= 1`) son **el contrato de verdad**, no los nombres: renombrar un campo es
compatible, cambiarle el número no. Un número reutilizado hace que un cliente antiguo lea basura sin que
nada falle.

## 4 · La implementación

```java
package com.ejemplo.grpc;

import com.ejemplo.grpc.proto.GreeterGrpc;
import com.ejemplo.grpc.proto.HelloReply;
import com.ejemplo.grpc.proto.HelloRequest;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

@Service
public class GreeterEndpoint extends GreeterGrpc.GreeterImplBase {

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        responseObserver.onNext(HelloReply.newBuilder()
                .setMessage("Hola " + request.getName())
                .build());
        responseObserver.onCompleted();
    }
}
```

⚠️ **`onCompleted()` no es opcional.** Si te lo dejas, el cliente se queda esperando hasta que expire su
tiempo de espera —o para siempre, si no lo tiene puesto—. Y en caso de error, `onError(...)`: una
excepción escapada se convierte en un `UNKNOWN` sin explicación al otro lado.

Fíjate en que la clase se llama `GreeterEndpoint` y no `Greeter`: el nombre del servicio del `.proto` ya
lo usan las clases generadas.

## 5 · Seguridad: viene protegido de serie

Igual que con WebSocket, el primer intento de llamada falló:

```
UNAUTHENTICATED: Authentication failed
```

Está bien así. El token va en la **metadata** de la llamada, que es el equivalente en gRPC a las
cabeceras HTTP:

```java
Metadata metadata = new Metadata();
metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);

var stub = GreeterGrpc.newBlockingStub(canal)
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
```

En producción, esto va en un interceptor del canal que renueva el token, no pegado a cada llamada.

## 6 · El test

Dos casos: con token y sin él. El segundo es el que asegura que no se te ha abierto una puerta trasera.

```java
package com.ejemplo.grpc;

import com.ejemplo.grpc.proto.GreeterGrpc;
import com.ejemplo.grpc.proto.HelloReply;
import com.ejemplo.grpc.proto.HelloRequest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
// Levanta el servidor en un transporte de pruebas y hace que el canal "local" apunte a el. Sin esto hay
// que adivinar el puerto, que con `port=0` es aleatorio.
@AutoConfigureTestGrpcTransport
@ActiveProfiles("test")
class GrpcIT {

    @Autowired
    private GrpcChannelFactory canales;

    @Value("${app.dev.jwt-secret}")
    private String secreto;

    private GreeterGrpc.GreeterBlockingStub stub() {
        return GreeterGrpc.newBlockingStub(canales.createChannel("local"));
    }

    private GreeterGrpc.GreeterBlockingStub stubAutenticado() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .subject("test-grpc")
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .build());
        jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + jwt.serialize());

        return stub().withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    @Test
    void saludaConTokenValido() throws Exception {
        HelloReply respuesta = stubAutenticado()
                .sayHello(HelloRequest.newBuilder().setName("mundo").build());

        assertEquals("Hola mundo", respuesta.getMessage());
    }

    @Test
    void sinTokenNoResponde() {
        assertThrows(StatusRuntimeException.class,
                () -> stub().sayHello(HelloRequest.newBuilder().setName("mundo").build()));
    }
}
```

```shell
./mvnw verify -Dit.test=GrpcIT
```

## Tropiezos

**El canal `"local"` necesita `@AutoConfigureTestGrpcTransport`.** El primer intento apuntaba a
`0.0.0.0:0` a mano y daba `UNAVAILABLE: io exception`, que suena a red caída y en realidad es «no hay
nadie en ese puerto»: con `spring.grpc.server.port=0` el puerto es aleatorio y el test no lo sabe.

**gRPC no aparece en el contrato OpenAPI.** Los clientes Java y Python que genera el proyecto cubren solo
la API REST. Quien consuma el gRPC genera sus stubs desde el `.proto`, así que ese fichero hay que
publicarlo en algún sitio donde lo encuentren — y versionarlo como lo que es.

**Comprueba que el `.proto` viaja al jar.** El plugin lo compila desde `src/main/proto`, pero si otro
servicio necesita el fichero, tendrás que publicarlo aparte. Un `.proto` que solo existe en tu repositorio
es un contrato que nadie más puede leer.
