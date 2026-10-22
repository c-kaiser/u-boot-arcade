{{- define "uboot.fullname" -}}
{{- .Release.Name | trunc 40 | trimSuffix "-" -}}
{{- end -}}

{{- define "uboot.labels" -}}
app.kubernetes.io/part-of: u-boot-arcade
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{/* Selector-Labels einer Komponente. Aufruf: include "uboot.selector" (list . "physics") */}}
{{- define "uboot.selector" -}}
{{- $root := index . 0 -}}
app.kubernetes.io/name: {{ index . 1 }}
app.kubernetes.io/instance: {{ $root.Release.Name }}
{{- end -}}

{{/* Image einer Komponente. Aufruf: include "uboot.image" (list . "physics") */}}
{{- define "uboot.image" -}}
{{- $root := index . 0 -}}
{{- printf "%s/%s-%s:%s" $root.Values.image.registry $root.Values.image.prefix (index . 1) $root.Values.image.tag -}}
{{- end -}}

{{/* Service-Name einer Komponente */}}
{{- define "uboot.svc" -}}
{{- $root := index . 0 -}}
{{- printf "%s-%s" (include "uboot.fullname" $root) (index . 1) -}}
{{- end -}}

{{/* Gemeinsame OTel-Umgebungsvariablen. Aufruf: include "uboot.otelEnv" (list . "physics") */}}
{{- define "uboot.otelEnv" -}}
{{- $root := index . 0 -}}
{{- $o := $root.Values.otel -}}
- name: OTEL_SERVICE_NAME
  value: {{ index . 1 | quote }}
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: {{ $o.endpoint | quote }}
- name: OTEL_EXPORTER_OTLP_PROTOCOL
  value: {{ $o.protocol | quote }}
- name: OTEL_METRIC_EXPORT_INTERVAL
  value: {{ $o.metricExportInterval | quote }}
- name: OTEL_TRACES_SAMPLER
  value: {{ $o.sampler | quote }}
- name: OTEL_TRACES_SAMPLER_ARG
  value: {{ $o.samplerArg | quote }}
- name: K8S_POD_NAME
  valueFrom: { fieldRef: { fieldPath: metadata.name } }
- name: K8S_NAMESPACE
  valueFrom: { fieldRef: { fieldPath: metadata.namespace } }
- name: K8S_NODE_NAME
  valueFrom: { fieldRef: { fieldPath: spec.nodeName } }
- name: OTEL_RESOURCE_ATTRIBUTES
  value: "service.namespace=u-boot-arcade,deployment.environment.name={{ $o.environment }},k8s.pod.name=$(K8S_POD_NAME),k8s.namespace.name=$(K8S_NAMESPACE),k8s.node.name=$(K8S_NODE_NAME){{ if $o.extraResourceAttributes }},{{ $o.extraResourceAttributes }}{{ end }}"
{{- end -}}

{{/* Standard-Probes für Spring-Boot-Services */}}
{{- define "uboot.probes" -}}
readinessProbe:
  httpGet: { path: /actuator/health, port: http }
  initialDelaySeconds: 10
  periodSeconds: 5
livenessProbe:
  httpGet: { path: /actuator/health, port: http }
  initialDelaySeconds: 60
  periodSeconds: 10
{{- end -}}
