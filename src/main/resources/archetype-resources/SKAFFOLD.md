# Skaffold - Guía de Uso

Esta guía documenta el uso de Skaffold para el desarrollo, testing y despliegue de la aplicación Spring Boot en Kubernetes.

#[[##]]# Tabla de Contenidos

- [¿Qué es Skaffold?](#qué-es-skaffold)
- [Requisitos Previos](#requisitos-previos)
- [Inicio Rápido](#inicio-rápido)
- [Configuración](#configuración)
- [Comandos Principales](#comandos-principales)
- [Profiles (Perfiles)](#profiles-perfiles)
- [Desarrollo Local](#desarrollo-local)
- [Testing](#testing)
- [Despliegue a Entornos](#despliegue-a-entornos)
- [Debug Remoto](#debug-remoto)
- [Port Forwarding](#port-forwarding)
- [Flujos de Trabajo Comunes](#flujos-de-trabajo-comunes)
- [Integración con Makefile](#integración-con-makefile)
- [Troubleshooting](#troubleshooting)
- [Mejores Prácticas](#mejores-prácticas)

#[[##]]# ¿Qué es Skaffold?

Skaffold es una herramienta de línea de comandos que facilita el desarrollo continuo de aplicaciones Kubernetes. Automatiza:

- 🔨 **Build** de imágenes Docker
- 📤 **Push** de imágenes a registries
- 🚀 **Deploy** a Kubernetes
- 🔄 **Hot reload** durante el desarrollo
- 🔍 **Logs** agregados de múltiples pods
- 🧪 **Testing** automatizado

#[[##]]# Requisitos Previos

#[[###]]# Software necesario

- **Skaffold**: v2.0 o superior
  ```bash
  # Instalación en Linux/macOS
  curl -Lo skaffold https://storage.googleapis.com/skaffold/releases/latest/skaffold-linux-amd64
  chmod +x skaffold
  sudo mv skaffold /usr/local/bin

  # Instalación en Windows (con Chocolatey)
  choco install skaffold

  # Verificar instalación
  skaffold version
  ```

- **Docker**: Para build de imágenes
- **Kubernetes**: Cluster local o remoto
  - Minikube
  - Docker Desktop with Kubernetes
  - Kind
  - K3s
  - GKE, EKS, AKS (producción)

- **kubectl**: Cliente de Kubernetes
  ```bash
  kubectl version --client
  ```

#[[###]]# Configuración inicial

1. **Iniciar cluster de Kubernetes local**:
   ```bash
   # Con Minikube
   minikube start

   # Con Docker Desktop
   # Habilitar Kubernetes en Docker Desktop settings

   # Con Kind
   kind create cluster
   ```

2. **Verificar conexión**:
   ```bash
   kubectl cluster-info
   kubectl get nodes
   ```

3. **Crear namespaces** (opcional):
   ```bash
   kubectl create namespace dev
   kubectl create namespace test
   kubectl create namespace staging
   kubectl create namespace production
   ```

#[[##]]# Inicio Rápido

#[[###]]# Desarrollo rápido con hot reload

```bash
skaffold dev
```

Este comando:
1. Construye la imagen Docker
2. Despliega a Kubernetes
3. Configura port forwarding automático
4. Monitorea cambios en el código
5. Reconstruye y redespliega automáticamente
6. Muestra logs en tiempo real

#[[###]]# Desplegar una vez y salir

```bash
skaffold run
```

#[[###]]# Limpiar recursos

```bash
skaffold delete
```

#[[##]]# Configuración

El archivo `skaffold.yaml` en la raíz del proyecto contiene toda la configuración.

#[[###]]# Estructura del archivo

```yaml
apiVersion: skaffold/v4beta11
kind: Config
metadata:
  name: ${artifactId}

build:          # Configuración de build
deploy:         # Configuración de deployment
portForward:    # Port forwarding automático
profiles:       # Perfiles para diferentes entornos
verify:         # Verificaciones post-deployment
```

#[[###]]# Variables disponibles

- `{{.VERSION}}`: Versión de la aplicación (desde git)
- `{{.IMAGE_TAG}}`: Tag de la imagen Docker
- `${artifactId}`: Nombre del proyecto

#[[##]]# Comandos Principales

#[[###]]# `skaffold dev`

Modo desarrollo con hot reload continuo.

```bash
# Desarrollo básico
skaffold dev

# Con profile específico
skaffold dev -p dev

# Con port forwarding manual
skaffold dev --port-forward=user

# Sin limpiar al salir
skaffold dev --cleanup=false

# Con verbosidad
skaffold dev -v info
```

**Características**:
- ✅ Hot reload automático
- ✅ Logs en tiempo real
- ✅ Port forwarding automático
- ✅ Rebuilds incrementales
- ✅ Limpieza automática al salir (Ctrl+C)

#[[###]]# `skaffold run`

Ejecuta build, deploy y sale.

```bash
# Deploy básico
skaffold run

# Con profile de staging
skaffold run -p staging

# Con tag específico
skaffold run --tag=v1.2.3

# Con namespace específico
skaffold run --namespace=my-namespace
```

#[[###]]# `skaffold build`

Solo construye la imagen sin desplegar.

```bash
# Build básico
skaffold build

# Build y push a registry
skaffold build --push

# Con tag específico
skaffold build --tag=latest
```

#[[###]]# `skaffold deploy`

Solo despliega (asume que la imagen ya existe).

```bash
# Deploy con imágenes existentes
skaffold deploy

# Con profile
skaffold deploy -p prod

# Desde un archivo de build anterior
skaffold deploy --build-artifacts=build.json
```

#[[###]]# `skaffold debug`

Modo debug con remote debugging habilitado.

```bash
# Debug en puerto 5005
skaffold debug

# El debugger quedará expuesto en localhost:5005
```

#[[###]]# `skaffold delete`

Limpia todos los recursos desplegados.

```bash
# Limpiar recursos
skaffold delete

# Con profile específico
skaffold delete -p staging
```

#[[###]]# `skaffold render`

Renderiza los manifiestos sin desplegar.

```bash
# Ver manifiestos generados
skaffold render

# Con profile
skaffold render -p prod

# Guardar en archivo
skaffold render -p prod > manifests.yaml
```

#[[##]]# Profiles (Perfiles)

Los perfiles permiten diferentes configuraciones según el entorno.

#[[###]]# Profile: `dev` (Desarrollo)

```bash
skaffold dev -p dev
```

**Características**:
- Hot reload automático habilitado
- Sincronización de archivos Java y properties
- Namespace: default o dev
- 1 réplica
- Recursos mínimos

**Uso típico**: Desarrollo local diario

#[[###]]# Profile: `local` (Local con Docker Compose)

```bash
skaffold run -p local
```

**Características**:
- Usa Docker Compose en lugar de Kubernetes
- No requiere cluster de Kubernetes
- Usa `compose-app.yml`
- Ideal para testing rápido

**Uso típico**: Testing sin Kubernetes

#[[###]]# Profile: `test` (Testing)

```bash
skaffold run -p test
```

**Características**:
- Ejecuta tests Maven automáticamente
- Build multi-stage hasta target `build`
- Tag basado en SHA256
- Manifiestos en `k8s/test/`

**Uso típico**: CI/CD pipelines, testing automatizado

#[[###]]# Profile: `staging` (Pre-producción)

```bash
skaffold run -p staging
```

**Características**:
- 2 réplicas mínimas
- HPA configurado (2-5 réplicas)
- Push a registry habilitado
- Tag basado en git tags
- Namespace: staging
- Despliegue con Helm

**Uso típico**: Ambiente de staging/QA

#[[###]]# Profile: `prod` (Producción)

```bash
skaffold run -p prod
```

**Características**:
- 3 réplicas mínimas
- HPA agresivo (3-10 réplicas)
- PodDisruptionBudget
- Recursos altos (2Gi RAM, 2000m CPU)
- Smoke tests automáticos
- Push a registry obligatorio
- Namespace: production
- Despliegue con Helm

**Uso típico**: Producción

#[[###]]# Profile: `debug` (Debug Remoto)

```bash
skaffold debug
```

**Características**:
- Puerto 5005 expuesto para debugging
- JAVA_OPTS configurado para debug remoto
- Port forwarding automático
- Suspend=n (no espera al debugger)

**Uso típico**: Debugging en Kubernetes

#[[##]]# Desarrollo Local

#[[###]]# Workflow típico de desarrollo

1. **Iniciar desarrollo**:
   ```bash
   skaffold dev
   ```

2. **Hacer cambios en el código**:
   - Editar archivos `.java`, `.properties`, `.yaml`
   - Skaffold detecta cambios automáticamente
   - Reconstruye y redespliega

3. **Ver logs en tiempo real**:
   - Los logs aparecen automáticamente en la terminal
   - Puedes filtrar por servicio

4. **Acceder a la aplicación**:
   - http://localhost:8080 (port forwarding automático)
   - http://localhost:8080/actuator/health
   - http://localhost:8080/docs (Swagger)

5. **Detener**:
   - `Ctrl+C` limpia automáticamente los recursos

#[[###]]# Sincronización de archivos

Skaffold sincroniza automáticamente cambios en:
- `src/**/*.java`
- `src/**/*.properties`
- `src/**/*.yml`
- `src/**/*.yaml`

**Sin necesidad de rebuild completo** en modo `dev`.

#[[###]]# Desarrollo sin Kubernetes

Si no tienes Kubernetes disponible:

```bash
skaffold run -p local
```

Esto usa Docker Compose con `compose-app.yml`.

#[[##]]# Testing

#[[###]]# Testing automatizado

```bash
skaffold run -p test
```

Esto ejecuta automáticamente:
1. `./mvnw test`
2. `./mvnw verify`

#[[###]]# Testing manual post-deployment

```bash
# Desplegar
skaffold run -p test

# Ejecutar tests manualmente
kubectl exec -it deployment/${artifactId} -n test -- ./mvnw test

# Limpiar
skaffold delete -p test
```

#[[###]]# Verificaciones de health

Las verificaciones están configuradas en el perfil y se ejecutan automáticamente:

```yaml
verify:
  - name: health-check
    container:
      name: health-check
      image: curlimages/curl
      command: ["/bin/sh"]
      args:
        - -c
        - |
          curl -f http://${artifactId}:8080/actuator/health/readiness
          curl -f http://${artifactId}:8080/actuator/health/liveness
```

#[[##]]# Despliegue a Entornos

#[[###]]# Staging

```bash
# Desplegar a staging
skaffold run -p staging

# Ver estado
kubectl get all -n staging

# Ver logs
kubectl logs -f deployment/staging-${artifactId} -n staging

# Limpiar
skaffold delete -p staging
```

#[[###]]# Producción

⚠️ **IMPORTANTE**: Requiere configuración de registry y permisos.

```bash
# Configurar registry
export SKAFFOLD_DEFAULT_REPO=gcr.io/my-project

# Desplegar a producción
skaffold run -p prod

# Ver estado
kubectl get all -n production

# Ver HPA
kubectl get hpa -n production

# Ver smoke test
kubectl logs -l name=smoke-test -n production

# Rollback si es necesario
kubectl rollout undo deployment/prod-${artifactId} -n production
```

#[[###]]# Despliegue con versión específica

```bash
# Etiquetar en git
git tag v1.2.3
git push origin v1.2.3

# Desplegar con esa versión
skaffold run -p prod
```

#[[##]]# Debug Remoto

#[[###]]# Iniciar sesión de debug

```bash
skaffold debug
```

#[[###]]# Conectar desde IntelliJ IDEA

1. **Crear configuración de Remote JVM Debug**:
   - Run → Edit Configurations
   - Add New → Remote JVM Debug
   - Host: localhost
   - Port: 5005

2. **Iniciar debug**:
   - Poner breakpoints en el código
   - Ejecutar la configuración de debug

3. **Debuggear**:
   - Hacer requests a la aplicación
   - Los breakpoints se activarán

#[[###]]# Conectar desde VS Code

Agregar a `.vscode/launch.json`:

```json
{
  "type": "java",
  "name": "Debug Kubernetes",
  "request": "attach",
  "hostName": "localhost",
  "port": 5005
}
```

#[[##]]# Port Forwarding

#[[###]]# Port forwarding automático

Skaffold configura automáticamente port forwarding para:

| Servicio | Puerto Local | Puerto Remoto |
|----------|--------------|---------------|
| Aplicación | 8080 | 8080 |
| PostgreSQL | 5432 | 5432 |
| Redis | 6379 | 6379 |

#[[###]]# Port forwarding manual

Si necesitas control manual:

```bash
# Desactivar port forwarding automático
skaffold dev --port-forward=off

# Configurar manualmente
kubectl port-forward svc/${artifactId} 8080:8080
```

#[[###]]# Acceder a servicios

```bash
# Aplicación
curl http://localhost:8080/actuator/health

# PostgreSQL
psql -h localhost -p 5432 -U myuser -d postgres

# Redis
redis-cli -h localhost -p 6379
```

#[[##]]# Flujos de Trabajo Comunes

#[[###]]# Workflow 1: Desarrollo de nueva feature

```bash
# 1. Crear rama
git checkout -b feature/nueva-funcionalidad

# 2. Iniciar desarrollo
skaffold dev

# 3. Hacer cambios, ver actualizaciones en tiempo real
# ... editar código ...

# 4. Cuando esté listo, hacer commit
git add .
git commit -m "Nueva funcionalidad"

# 5. Detener skaffold (Ctrl+C)
```

#[[###]]# Workflow 2: Testing antes de merge

```bash
# 1. Ejecutar tests en Kubernetes
skaffold run -p test

# 2. Verificar que pasen
kubectl logs -f deployment/test-${artifactId} -n test

# 3. Limpiar
skaffold delete -p test

# 4. Si todo OK, hacer merge
git checkout main
git merge feature/nueva-funcionalidad
```

#[[###]]# Workflow 3: Deploy a staging

```bash
# 1. Asegurar que estás en main
git checkout main
git pull

# 2. Desplegar a staging
skaffold run -p staging

# 3. Verificar
kubectl get pods -n staging
curl http://staging-${artifactId}.staging.svc.cluster.local:8080/actuator/health

# 4. Si hay problemas, ver logs
kubectl logs -f deployment/staging-${artifactId} -n staging

# 5. Limpiar si es necesario
skaffold delete -p staging
```

#[[###]]# Workflow 4: Release a producción

```bash
# 1. Crear tag de versión
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0

# 2. Configurar registry
export SKAFFOLD_DEFAULT_REPO=gcr.io/my-project

# 3. Desplegar
skaffold run -p prod

# 4. Verificar deployment
kubectl rollout status deployment/prod-${artifactId} -n production

# 5. Verificar health
kubectl exec -it deployment/prod-${artifactId} -n production -- \
  curl http://localhost:8080/actuator/health

# 6. Monitorear
kubectl get hpa -n production -w
```

#[[###]]# Workflow 5: Debugging en Kubernetes

```bash
# 1. Iniciar modo debug
skaffold debug

# 2. Conectar debugger desde IDE (puerto 5005)

# 3. Poner breakpoints

# 4. Hacer requests a la aplicación
curl http://localhost:8080/api/endpoint

# 5. Debuggear en IDE

# 6. Detener (Ctrl+C)
```

#[[###]]# Workflow 6: Renderizar manifiestos para revisión

```bash
# Ver manifiestos que se desplegarían
skaffold render -p prod > prod-manifests.yaml

# Revisar
cat prod-manifests.yaml

# Desplegar manualmente si es necesario
kubectl apply -f prod-manifests.yaml
```

#[[##]]# Integración con Makefile

El proyecto incluye un Makefile que facilita el uso de Skaffold.

#[[###]]# Comandos integrados

```bash
# Desarrollo
make dev             # Equivale a: make docker-up && skaffold dev

# Build de imagen
make docker-build    # Usa Skaffold internamente

# Testing
make test            # Incluye tests en Kubernetes
```

#[[###]]# Uso combinado

```bash
# 1. Levantar servicios con Makefile
make docker-up

# 2. Desarrollar con Skaffold
skaffold dev

# 3. Tests con Makefile
make test

# 4. Limpiar
skaffold delete
make docker-down
```

#[[##]]# Troubleshooting

#[[###]]# Problema: Skaffold no detecta cambios

**Solución 1**: Verificar configuración de sync
```bash
skaffold dev -v debug
```

**Solución 2**: Forzar rebuild
```bash
# En otra terminal mientras skaffold dev está corriendo
touch src/main/java/MainApplication.java
```

#[[###]]# Problema: Build falla

**Solución 1**: Verificar Dockerfile
```bash
docker build -t test .
```

**Solución 2**: Limpiar cache de Docker
```bash
docker system prune -a
skaffold dev --cache-artifacts=false
```

#[[###]]# Problema: Deploy falla

**Solución 1**: Verificar manifiestos
```bash
skaffold render -p dev
kubectl apply --dry-run=client -f <(skaffold render -p dev)
```

**Solución 2**: Verificar contexto de kubectl
```bash
kubectl config current-context
kubectl config use-context <correct-context>
```

#[[###]]# Problema: Port forwarding no funciona

**Solución 1**: Verificar que el pod esté corriendo
```bash
kubectl get pods
kubectl logs <pod-name>
```

**Solución 2**: Hacer port forwarding manual
```bash
kubectl port-forward svc/${artifactId} 8080:8080
```

#[[###]]# Problema: Imágenes no se encuentran

**Solución**: Verificar registry y tag policy
```bash
# Ver qué imagen se está usando
kubectl describe pod <pod-name> | grep Image

# Verificar que la imagen existe localmente
docker images | grep ${artifactId}
```

#[[###]]# Problema: "No space left on device"

**Solución**: Limpiar Docker
```bash
docker system prune -a --volumes
minikube ssh -- docker system prune -a
```

#[[###]]# Problema: Cambios de properties no se reflejan

**Solución**: Reiniciar pod manualmente
```bash
kubectl rollout restart deployment/${artifactId}
```

#[[###]]# Problema: Health checks fallan

**Solución**: Verificar actuator endpoints
```bash
kubectl exec -it deployment/${artifactId} -- curl localhost:8080/actuator/health
kubectl logs deployment/${artifactId}
```

#[[##]]# Mejores Prácticas

#[[###]]# 1. Desarrollo

✅ **Usar `skaffold dev` siempre para desarrollo local**
- Hot reload automático
- Feedback inmediato
- Limpieza automática

✅ **Sincronización de archivos**
- Configura sync para archivos que cambian frecuentemente
- Evita rebuilds innecesarios

✅ **Port forwarding**
- Usa port forwarding automático
- Documenta puertos usados

#[[###]]# 2. Testing

✅ **Profile dedicado para tests**
- Usa `-p test` para CI/CD
- Ejecuta tests automáticamente
- Usa namespace separado

✅ **Verificaciones post-deploy**
- Configura health checks
- Verifica endpoints críticos
- Usa smoke tests

#[[###]]# 3. Staging/Producción

✅ **Tags basados en git**
- Usa tags semánticos (v1.2.3)
- Tag policy: gitCommit con variant Tags

✅ **Registry remoto**
- Configura SKAFFOLD_DEFAULT_REPO
- Push obligatorio para ambientes remotos

✅ **Helm para gestión compleja**
- Usa Helm charts para prod
- Valores diferentes por entorno

#[[###]]# 4. Seguridad

⚠️ **No commitear secrets**
- Usa Sealed Secrets o External Secrets
- Variables sensibles por variable de entorno

⚠️ **Image scanning**
- Escanea imágenes antes de prod
- Usa tags inmutables

#[[###]]# 5. Performance

⚠️ **BuildKit**
- Habilita BuildKit para builds más rápidos
- Usa cache layers eficientemente

⚠️ **Resource limits**
- Define requests y limits apropiados
- Monitorea uso real

#[[###]]# 6. Monitoreo

✅ **Logs centralizados**
- Skaffold agrega logs automáticamente
- Usa labels para filtrar

✅ **Health checks**
- Configura liveness, readiness y startup probes
- Usa actuator endpoints de Spring Boot

#[[##]]# Comandos de Referencia Rápida

```bash
# Desarrollo
skaffold dev                    # Desarrollo con hot reload
skaffold dev -p dev            # Con profile dev
skaffold debug                  # Con debugging remoto

# Deploy
skaffold run                    # Deploy y salir
skaffold run -p staging        # Deploy a staging
skaffold run -p prod           # Deploy a producción

# Build
skaffold build                  # Solo build
skaffold build --push          # Build y push

# Utilities
skaffold render                 # Ver manifiestos
skaffold delete                 # Limpiar recursos
skaffold version                # Ver versión

# Profiles
-p dev                          # Desarrollo
-p local                        # Local con compose
-p test                         # Testing
-p staging                      # Staging
-p prod                         # Producción
-p debug                        # Debug remoto

# Flags útiles
--port-forward                  # Control de port forwarding
--tail                          # Seguir logs
--cache-artifacts=false        # Sin cache
-v info                         # Verbosidad
--namespace=<ns>               # Namespace específico
```

#[[##]]# Referencias

- [Skaffold Documentation](https://skaffold.dev/docs/)
- [Skaffold GitHub](https://github.com/GoogleContainerTools/skaffold)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Kubernetes Manifests - README.md](k8s/README.md)
- [Makefile - MAKEFILE.md](MAKEFILE.md)

#[[##]]# Soporte

Para más información:
- Ver logs detallados: `skaffold dev -v debug`
- Revisar configuración: `cat skaffold.yaml`
- Ver documentación de manifiestos: `cat k8s/README.md`
- Ejecutar: `skaffold help`
