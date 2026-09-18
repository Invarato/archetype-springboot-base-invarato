{{/*
Nombre base de los objetos.

⚠️ El `lower` y el `regexReplaceAll` NO son cosmetica. Los nombres de objeto de Kubernetes tienen que ser
etiquetas RFC 1123: minusculas, digitos y guiones. Un artifactId en camelCase (`miServicio`) genera un
chart que Helm renderiza sin quejarse y que el API server RECHAZA al desplegar — o sea, el fallo aparece
en el peor momento posible y lejos de aqui.

El truncado a 63 caracteres es por el mismo motivo: es el limite de una etiqueta, y pasarse da un rechazo
del API server que cuesta relacionar con la longitud del nombre.
*/}}
{{/*
⚠️ regexReplaceAll toma (regex, INPUT, replacement). En una tuberia el valor entra como ULTIMO
argumento, o sea como replacement, y el resultado sale vacio sin dar ningun error. Por eso se usa una
variable en vez de encadenarlo.
*/}}
{{- define "app.name" -}}
{{- $bruto := default .Chart.Name .Values.nameOverride | lower -}}
{{- regexReplaceAll "[^a-z0-9-]" $bruto "-" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "app.fullname" -}}
{{- $bruto := default (include "app.name" .) .Values.fullnameOverride | lower -}}
{{- regexReplaceAll "[^a-z0-9-]" $bruto "-" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Etiquetas estandar de Kubernetes. Merece la pena ponerlas aunque parezcan burocracia: son las que usan
kubectl, las herramientas de coste y los paneles para agrupar por aplicacion y por version.
*/}}
{{- define "app.labels" -}}
app.kubernetes.io/name: {{ include "app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{/*
  ⚠️ El `trunc 63` de aqui no sobra. Esta es la unica etiqueta que se construye a partir de un VALUE, y
  por eso se escapo del truncado que si tienen los nombres: una herramienta de despliegue que etiquete
  la imagen con el digest (skaffold lo hace: 64 caracteres) genera una etiqueta invalida y el API server
  rechaza el despliegue entero con «must be no more than 63 characters». No lo cazan ni `helm lint` ni
  `helm template`: solo se ve desplegando contra un cluster de verdad.
*/}}
app.kubernetes.io/version: {{ .Values.image.tag | default .Chart.AppVersion | trunc 63 | trimSuffix "-" | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end -}}

{{- define "app.selectorLabels" -}}
app.kubernetes.io/name: {{ include "app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
