# Kubernetes Manifests

Este directorio contiene los manifiestos de Kubernetes para desplegar la aplicación Spring Boot en diferentes entornos.

## Estructura

```
k8s/
├── deployment.yaml              # Deployment principal de la aplicación
├── service.yaml                 # Service para exponer la aplicación
├── configmap.yaml              # ConfigMap con configuración
├── secret.yaml                 # Secret con credenciales (cambiar en producción)
├── ingress.yaml                # Ingress para acceso externo
├── postgres-deployment.yaml    # PostgreSQL deployment, service y PVC
├── redis-deployment.yaml       # Redis deployment, service y PVC
├── README.md                   # Esta documentación
├── dev/                        # Manifiestos para desarrollo
│   └── kustomization.yaml
├── test/                       # Manifiestos para testing
│   └── kustomization.yaml
├── staging/                    # Manifiestos para staging
│   ├── kustomization.yaml
│   └── hpa.yaml
└── prod/                       # Manifiestos para producción
    ├── kustomization.yaml
    ├── hpa.yaml
    └── pdb.yaml
```

## Recursos Base

### deployment.yaml
- **Replicas**: 1 (ajustable por entorno)
- **Recursos**: 512Mi RAM / 250m CPU (requests)
- **Probes**: Liveness, Readiness y Startup configuradas
- **Variables de entorno**: Configuración de Spring Boot
- **Volúmenes**: ConfigMap montado en /config

### service.yaml
- **Tipo**: ClusterIP
- **Puerto**: 8080
- **Selector**: app=${artifactId}

### configmap.yaml
- Configuración de Spring Boot
- Actuator endpoints habilitados
- Configuración de JPA, Liquibase y Redis

### secret.yaml
⚠️ **IMPORTANTE**: Cambiar las credenciales por defecto en producción
- Usuario de base de datos: myuser
- Contraseña de base de datos: secret

### postgres-deployment.yaml
- PostgreSQL 18.0
- PersistentVolumeClaim de 5Gi
- Liveness y Readiness probes
- Credenciales desde Secret

### redis-deployment.yaml
- Redis 8.2.1
- PersistentVolumeClaim de 1Gi
- Probes configuradas

### ingress.yaml
- Ingress controller: nginx
- Host: ${artifactId}.local

## Entornos

### Development (dev/)
```bash
kubectl apply -k k8s/dev/
```

**Características**:
- 1 réplica
- 256Mi RAM / 512Mi límite
- Namespace: dev
- Perfil Spring: dev

### Test (test/)
```bash
kubectl apply -k k8s/test/
```

**Características**:
- 1 réplica
- Namespace: test
- Perfil Spring: test
- Sin Ingress

### Staging (staging/)
```bash
kubectl apply -k k8s/staging/
```

**Características**:
- 2 réplicas mínimas
- HPA: 2-5 réplicas
- 512Mi-1Gi RAM / 500m-1000m CPU
- Namespace: staging
- Perfil Spring: staging
- Autoscaling basado en CPU (70%) y Memoria (80%)

### Production (prod/)
```bash
kubectl apply -k k8s/prod/
```

**Características**:
- 3 réplicas mínimas
- HPA: 3-10 réplicas
- 1Gi-2Gi RAM / 1000m-2000m CPU
- Namespace: production
- Perfil Spring: prod
- PodDisruptionBudget (mínimo 2 pods disponibles)
- Autoscaling agresivo

## Uso con kubectl

### Desplegar en el entorno por defecto
```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/redis-deployment.yaml
```

### Desplegar con Kustomize
```bash
# Desarrollo
kubectl apply -k k8s/dev/

# Test
kubectl apply -k k8s/test/

# Staging
kubectl apply -k k8s/staging/

# Producción
kubectl apply -k k8s/prod/
```

### Ver recursos desplegados
```bash
# Desarrollo
kubectl get all -n dev

# Staging
kubectl get all -n staging

# Producción
kubectl get all -n production
```

### Ver logs
```bash
# Ver logs de la aplicación
kubectl logs -f deployment/${artifactId} -n dev

# Ver logs de PostgreSQL
kubectl logs -f deployment/postgres -n dev
```

### Port forwarding para testing local
```bash
# Aplicación
kubectl port-forward svc/${artifactId} 8080:8080 -n dev

# PostgreSQL
kubectl port-forward svc/postgres 5432:5432 -n dev

# Redis
kubectl port-forward svc/redis 6379:6379 -n dev
```

### Escalar manualmente
```bash
kubectl scale deployment/${artifactId} --replicas=3 -n dev
```

### Ver HPA
```bash
kubectl get hpa -n staging
kubectl describe hpa ${artifactId}-hpa -n staging
```

## Uso con Skaffold

Skaffold está configurado para usar estos manifiestos automáticamente.

### Desarrollo local
```bash
skaffold dev
```

### Desplegar en entorno específico
```bash
# Development
skaffold run -p dev

# Test
skaffold run -p test

# Staging
skaffold run -p staging

# Production
skaffold run -p prod
```

### Debug remoto
```bash
skaffold debug
```

## Configuración Adicional

### Cambiar credenciales de base de datos

1. Crear un nuevo secret:
```bash
kubectl create secret generic ${artifactId}-secret \
  --from-literal=db-username=nuevo_usuario \
  --from-literal=db-password=nueva_contraseña \
  -n production
```

2. O editar el secret existente:
```bash
kubectl edit secret ${artifactId}-secret -n production
```

### Agregar variables de entorno adicionales

Editar `configmap.yaml` o agregar variables en `deployment.yaml`:

```yaml
env:
  - name: MI_VARIABLE
    value: "mi_valor"
```

### Configurar Ingress con TLS

Agregar a `ingress.yaml`:

```yaml
spec:
  tls:
    - hosts:
        - ${artifactId}.example.com
      secretName: ${artifactId}-tls
```

### Modificar recursos por entorno

Editar el archivo `kustomization.yaml` del entorno correspondiente.

## Troubleshooting

### Pod no inicia
```bash
kubectl describe pod <pod-name> -n <namespace>
kubectl logs <pod-name> -n <namespace>
```

### Probes fallan
```bash
# Verificar health endpoint
kubectl exec -it <pod-name> -n <namespace> -- curl localhost:8080/actuator/health
```

### Base de datos no conecta
```bash
# Verificar que postgres esté corriendo
kubectl get pods -l app=postgres -n <namespace>

# Probar conexión
kubectl exec -it <pod-name> -n <namespace> -- nc -zv postgres 5432
```

### HPA no escala
```bash
# Verificar métricas
kubectl top pods -n <namespace>

# Ver eventos del HPA
kubectl describe hpa ${artifactId}-hpa -n <namespace>
```

## Mejores Prácticas

1. **Secrets**: Usar herramientas como Sealed Secrets o external-secrets en producción
2. **Resources**: Ajustar requests y limits basándose en métricas reales
3. **Probes**: Ajustar timeouts según el tiempo de inicio de la aplicación
4. **Namespaces**: Usar namespaces separados por entorno
5. **Labels**: Mantener labels consistentes para facilitar la gestión
6. **Backups**: Configurar backups automáticos para PostgreSQL
7. **Monitoring**: Integrar con Prometheus/Grafana para monitoreo

## Referencias

- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Kustomize](https://kustomize.io/)
- [Skaffold](https://skaffold.dev/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
