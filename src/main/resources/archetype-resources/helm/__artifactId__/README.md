#[[#]]# Chart de ${artifactId}

Despliegue del servicio en Kubernetes. Los valores por defecto están en `values.yaml`; cada entorno
sobreescribe lo suyo con su fichero.

```shell
helm upgrade --install ${artifactId} ./helm/${artifactId} \
  -f helm/${artifactId}/values-prod.yaml \
  --set image.tag=1.2.3
```

⚠️ **`image.tag` va siempre explícito.** Sin versión fijada no se sabe qué está corriendo y un rollback
deja de ser repetible.

#[[##]]# Lo que este chart NO hace, a propósito

- **No despliega Postgres ni Redis.** Una base de datos dentro del clúster con un `Deployment` normal es
  pérdida de datos esperando a pasar: no tolera escalado ni actualizaciones rolling. Usa un servicio
  gestionado o un operador. Para desarrollo local ya está `compose.yaml`.
- **No crea el Secret.** El chart espera uno que ya exista en el namespace, con las claves `db-username`
  y `db-password`. Poner credenciales en un `values.yaml` versionado las publica en el historial de git
  para siempre, y rotarlas después no las borra de ahí.

```shell
kubectl create secret generic ${artifactId}-secret \
  --from-literal=db-username=... --from-literal=db-password=...
```

En un entorno serio, esto lo aporta el gestor de secretos de tu nube, External Secrets o Sealed Secrets.

#[[##]]# Detalles que ya están resueltos

- **`startupProbe`** además de liveness: da margen al arranque sin relajar la detección de cuelgues. Es lo
  que evita el bucle de reinicios en un arranque lento, que parece un fallo de la aplicación y no lo es.
- **Reinicio al cambiar configuración**: el Deployment lleva el checksum del ConfigMap. Sin eso, un
  `helm upgrade` que solo toca configuración no reinicia nada y te quedas con la vieja corriendo.
- **`readOnlyRootFilesystem`** con un `emptyDir` montado en `/tmp`, que es lo que la JVM necesita para
  arrancar con el sistema de ficheros en solo lectura.
- **Sin límite de CPU**: el límite de CPU no protege al nodo (para eso está `requests`) y provoca
  throttling que se manifiesta como latencia inexplicable. El de memoria sí está, porque una fuga sí se
  lleva el nodo por delante.
- **Nombres en minúsculas**: los objetos de Kubernetes exigen RFC 1123, así que el chart normaliza el
  nombre. Un `artifactId` en camelCase generaría objetos que el API server rechaza al desplegar.
