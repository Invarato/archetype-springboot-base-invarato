# Recetas

Cómo añadir a un proyecto ya generado las integraciones que **no vienen de serie**: Kafka, WebSocket y
gRPC.

## Por qué no vienen dentro

Por la regla de [D12](../base-del-proyecto.md): *lo que entra en el arquetipo lo ejercita un test de la
puerta*. Y estas tres no son «una dependencia más» — cambian la forma de la aplicación: Kafka mete un
broker en la puerta de calidad, gRPC trae un segundo puerto y una cadena de herramientas de protos, y
WebSocket abre conexiones largas en un servicio pensado como stateless. Meterlas de serie saldría cara a
todos los proyectos generados para que la aprovechen unos pocos.

La alternativa tampoco era «me lo descargo y borro lo que no use»: eso ya se probó sin querer en este
mismo repo y no funcionó. Dos dependencias llevaban tiempo dentro con **cero usos** y nadie las quitó,
porque una dependencia sin ejemplo es invisible.

## Cómo están escritas

**Cada receta se aplicó de verdad a un proyecto generado antes de escribirla.** Ninguna sale de la
memoria ni de copiar documentación: los pasos son los que funcionaron, los tropiezos son los que
aparecieron al hacerlo, y las cifras están medidas. Cada una acaba con un test que puedes ejecutar para
comprobar que lo has montado bien.

| Receta | Qué te deja funcionando | Lo que más sorprende |
|---|---|---|
| **[Kafka](kafka.md)** | Publicar y consumir eventos, con un test contra un broker real | Los listeners metían **14 errores de conexión** en tests que no usan Kafka |
| **[WebSocket](websocket.md)** | Un endpoint con eco y su test de ida y vuelta | El handshake da **401**: la seguridad del arquetipo también lo protege |
| **[gRPC](grpc.md)** | Servicio gRPC generado desde un `.proto`, con test autenticado | Igual que arriba: el servicio nace **protegido**, y hay que llevar el token en la metadata |
| **[Kubernetes local](kubernetes-local.md)** | Un clúster de verdad en tu máquina, con cada cambio desplegándose solo | `helm lint` no valida los nombres de objeto: desplegar destapó **dos fallos del chart** |

Los ejemplos usan `com.ejemplo` como paquete base. Sustitúyelo por el tuyo — el mismo que le diste al
generar el proyecto.

## Antes de dar una receta por buena

Estas páginas envejecen: las versiones las gobierna el BOM de Spring Boot y cambian sin que nadie toque
este repo. Cada receta dice **con qué versiones se verificó y cuándo**. Si la tuya no coincide, trátala
como una guía y no como una garantía: lo que no cambia es el orden de los pasos y los tropiezos, que son
la parte que cuesta descubrir.
