# Receta · Iterar contra un clúster local (kind + Skaffold + Helm)

Levantar un Kubernetes de verdad en tu máquina y que cada cambio en el código llegue al clúster solo.

> **Verificado con** arquetipo 2.0.0 · kind v0.30.0 (Kubernetes v1.34.0) · Skaffold v2.16.1 ·
> Helm v3.19.0 · **dentro de un devcontainer con Docker-in-Docker** · 2026-09-18.
> El pod llegó a `READY` desplegado por Skaffold con el chart de `helm/` de este mismo proyecto.

## Qué cuesta, y cuándo NO merece la pena

El bucle normal de este proyecto —`make run`, con Spring Boot levantando el compose— arranca en
segundos. **Un clúster nunca va a ganar a eso**: aquí cada cambio son `mvn package` + `docker build` +
`kind load` + rollout, del orden de un minuto.

Así que esto no sustituye al bucle normal. Compensa cuando lo que necesitas probar **es Kubernetes**:

- Probes, límites de recursos, reinicios y arranques lentos.
- ConfigMaps, Secrets y variables inyectadas como en producción.
- Varios servicios hablando entre sí por nombre de `Service`.
- Ingress, políticas de red, service mesh.
- El propio chart de Helm: **`helm lint` y `helm template` no validan los nombres de objeto**. Eso lo
  hace el API server, y solo se entera desplegando (ver los tropiezos).

Si lo que cambias es lógica de negocio, quédate en `make run`.

## 1 · Un clúster: kind

`kind` levanta Kubernetes dentro de contenedores Docker. **Funciona dentro del devcontainer** de este
proyecto sin nada especial, porque ya trae Docker-in-Docker.

```shell
curl -sSLo /usr/local/bin/kind https://kind.sigs.k8s.io/dl/v0.30.0/kind-linux-amd64
chmod +x /usr/local/bin/kind

kind create cluster --name dev --wait 120s
```

**¿kind o minikube?** Los dos valen y minikube es perfectamente razonable —si ya lo usas, sigue—. Para
un devcontainer kind encaja mejor: el nodo *es* un contenedor, así que no hay una VM ni un segundo
demonio de Docker debajo, y `kind load docker-image` mete tu imagen en el clúster sin registro de por
medio. minikube con `--driver=docker` hace algo parecido; con otros drivers, dentro de un contenedor se
complica.

## 2 · Las herramientas

```shell
# Skaffold
curl -sSLo /usr/local/bin/skaffold https://github.com/GoogleContainerTools/skaffold/releases/download/v2.16.1/skaffold-linux-amd64
chmod +x /usr/local/bin/skaffold

# kubectl y helm: los necesita Skaffold en el PATH, no solo tú
```

⚠️ **Skaffold invoca `helm` y `kubectl` como binarios externos.** Si no están, falla con
`Helm not found` o con `exec: "kubectl": executable file not found`, ya en mitad del despliegue y con
la imagen construida. Compruébalo antes: `helm version && kubectl version --client`.

## 3 · Postgres y Redis dentro del clúster

El chart de `helm/` **no despliega la base de datos**, y es deliberado ([D10](../base-del-proyecto.md)):
una base de datos como `Deployment` normal es pérdida de datos esperando a pasar.

Para un clúster **local y desechable** eso no aplica, así que aquí sí van dentro. Guárdalo aparte del
chart —por ejemplo en `k8s-dev/infra.yaml`— para que no haya ninguna duda de que no es para producción:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: {name: postgres, namespace: dev}
spec:
  replicas: 1
  selector: {matchLabels: {app: postgres}}
  template:
    metadata: {labels: {app: postgres}}
    spec:
      containers:
        - name: postgres
          image: postgres:18.6
          env:
            - {name: POSTGRES_DB, value: postgres}
            - {name: POSTGRES_USER, value: myuser}
            - {name: POSTGRES_PASSWORD, value: secret}
          ports: [{containerPort: 5432}]
---
apiVersion: v1
kind: Service
metadata: {name: postgres, namespace: dev}
spec:
  selector: {app: postgres}
  ports: [{port: 5432, targetPort: 5432}]
```

(Y lo mismo para `redis:8.10.1` en el 6379.)

El chart espera un Secret **que ya exista**, con las claves `db-username` y `db-password`:

```shell
kubectl create namespace dev
kubectl apply -f k8s-dev/infra.yaml
kubectl -n dev create secret generic mi-servicio-secret \
  --from-literal=db-username=myuser \
  --from-literal=db-password=secret
```

El nombre por defecto del Secret es `<nombre-del-release>-secret`.

## 4 · `skaffold.yaml`

En la raíz del proyecto:

```yaml
apiVersion: skaffold/v4beta13
kind: Config
metadata:
  name: mi-servicio

build:
  local:
    push: false          # kind carga la imagen directamente: no hace falta registro
  artifacts:
    - image: mi-servicio
      # `custom` y no `docker`: ver el primer tropiezo.
      custom:
        buildCommand: ./mvnw -q -pl app -am package -DskipTests && docker build -t "$IMAGE" .
        dependencies:
          paths:
            - pom.xml
            - Dockerfile
            - app/pom.xml
            - app/src/**
            - contract/src/**

deploy:
  helm:
    releases:
      - name: app
        chartPath: helm/mi-servicio       # el chart que ya tiene el proyecto
        namespace: dev
        createNamespace: true
        setValues:
          database.host: postgres
          env.SPRING_PROFILES_ACTIVE: dev
          # ⚠️ Imprescindible: ver el segundo tropiezo.
          env.SPRING_DOCKER_COMPOSE_ENABLED: "false"
          env.SPRING_DATA_REDIS_HOST: redis
        setValueTemplates:
          image.repository: "{{.IMAGE_REPO_mi_servicio}}"
          image.tag: "{{.IMAGE_TAG_mi_servicio}}"
```

## 5 · A trabajar

```shell
skaffold dev   --kube-context kind-dev    # se queda vigilando: cada cambio se reconstruye y redespliega
skaffold run   --kube-context kind-dev    # una sola vez
skaffold delete --kube-context kind-dev   # limpia lo desplegado
```

Con `skaffold dev` también tienes los logs de los pods en la terminal y reenvío de puertos automático.

## Tropiezos

**El builder `docker` con `hooks.before` NO sirve para Java.** Parece lo natural —construir el jar
antes— y falla:

```
getting hash for artifact: file pattern [app/target/*.jar] must match at least one file
```

Skaffold calcula el hash de las dependencias **antes** de ejecutar el hook, así que el jar tiene que
existir ya. Con un builder `custom` el comando entero (Maven + Docker) es tuyo, y `dependencies.paths`
le dice a Skaffold qué vigilar — que además es lo que hace útil a `skaffold dev`.

**El perfil `dev` no se puede usar tal cual dentro del clúster.** En este proyecto ese perfil activa el
soporte de Docker Compose de Spring Boot, y dentro de un pod no hay compose que levantar: el arranque
falla. De ahí el `SPRING_DOCKER_COMPOSE_ENABLED: "false"` de arriba. Sigues teniendo del perfil `dev` lo
que quieres en un clúster de pruebas: el emisor de JWT local, la documentación navegable y el SQL a la
vista.

**`helm lint` y `helm template` no validan los nombres de objeto.** Montar esta receta destapó **dos
fallos del chart** que llevaban ahí desde el principio y que ninguna de esas dos órdenes ve, porque
quien valida los nombres es el API server:

- Un `artifactId` en camelCase generaba un nombre de Secret inválido
  («a lowercase RFC 1123 subdomain must consist of lower case alphanumeric characters»).
- La etiqueta `app.kubernetes.io/version` se construía con la etiqueta de la imagen, y Skaffold etiqueta
  con el digest: 64 caracteres, uno más del límite de una etiqueta. Rechazo del despliegue entero.

Los dos están corregidos en el arquetipo. La lección vale para cualquier chart: **desplegarlo una vez
contra un clúster desechable encuentra lo que el linter no puede.**

## ¿Skaffold, Tilt o DevSpace?

Los tres resuelven lo mismo —el bucle de cambio→imagen→despliegue— con filosofías distintas:

| | Cómo se configura | En qué destaca |
|---|---|---|
| **Skaffold** | YAML declarativo (`skaffold.yaml`) | El más parecido a un pipeline: los mismos perfiles sirven para CI. Integra Helm, kustomize y kubectl de serie. Es el que encaja aquí porque **reutiliza el chart que ya tienes**. |
| **Tilt** | Un `Tiltfile` en Starlark (Python) | Es un programa, no un fichero de datos: expresa casos raros sin pelearse con YAML. Su panel web para varios servicios a la vez es mejor que nada de lo que tiene Skaffold. |
| **DevSpace** | YAML + un modo de desarrollo dentro del pod | Su apuesta es distinta: en vez de reconstruir la imagen a cada cambio, **sincroniza ficheros dentro del contenedor en marcha** y reinicia el proceso allí. El bucle es mucho más corto; a cambio, lo que corre deja de ser exactamente la imagen que desplegarás. |

**¿Alguno supera a Skaffold?** No de forma que justifique cambiar si ya te funciona. Tilt gana con
muchos servicios a la vez; DevSpace gana en velocidad de bucle si aceptas su compromiso. Para **un**
servicio con un chart de Helm, Skaffold es la opción con menos piezas nuevas.

Y ojo con una confusión frecuente: **Skaffold no sustituye a Helm**. Skaffold es el bucle; Helm es lo
que despliega. En esta receta Skaffold *llama* a Helm con el chart del proyecto.
