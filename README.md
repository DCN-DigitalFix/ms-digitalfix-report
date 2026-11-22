# ms-digitalfix-report

Consumidor de Kafka del sistema DigitalFix, con **dos** consumidores sobre
**dos** tópicos distintos:

1. Escucha los mismos eventos de dominio que `ms-digitalfix-audit`
   (`digitalfix.workorders.events`), pero en vez de un historial completo
   mantiene una sola fila por orden con su estado actual.
2. Escucha el registro normalizado que `ms-digitalfix-audit` publica
   (`digitalfix.audit.events`) y lleva la cuenta de cuántos eventos de
   auditoría existen en total.

Expone dos reportes de solo lectura: un resumen agregado (que incluye ese
total de auditoría) y el certificado de una orden cerrada. No expone API de
escritura.

---

## Por qué existe junto a audit

Los dos microservicios consumen `digitalfix.workorders.events`, cada uno con
su propio consumer group (`audit-timeline` y `report-metrics`). Ninguno le
quita mensajes al otro: Kafka entrega el tópico completo a cada grupo. Es la
misma fuente de eventos resuelta con dos modelos de datos distintos porque
sirven preguntas distintas:

| | audit | report |
|---|---|---|
| Pregunta que responde | ¿Qué pasó con esta orden, en orden cronológico? | ¿Cómo está el conjunto de órdenes ahora mismo? |
| Forma de los datos | Un registro por evento (historial, append-only) | Una fila por orden (estado actual, se sobrescribe) |
| Clave de idempotencia | `eventId` como clave primaria | comparar `occurredAt` contra el último aplicado |

---

## Topología

| Rol | Tópico | Grupo |
|---|---|---|
| Consume | `digitalfix.workorders.events` | `report-metrics`, concurrencia 3 (una por partición) |
| Consume | `digitalfix.audit.events` | `report-audit-trail` |
| DLT | `digitalfix.workorders.events.DLT` | — |

Los dos consumidores son independientes entre sí: viven en clases distintas
(`WorkOrderEventConsumer` y `AuditTrailConsumer`), con su propio grupo, su
propio parser (`WorkOrderEventParser` / `AuditRecordEventParser`) y su propia
tabla (`order_summary` / `audit_trail_event`). Uno puede fallar o atrasarse
sin afectar al otro.

Mismo manejo de errores en ambos: `ErrorNoRecuperableException` (payload
inválido) va directo a la DLT del tópico correspondiente; cualquier otra
excepción reintenta con backoff exponencial (1 s, x2, tope 8 s, 3 intentos)
antes de derivar ahí. `report` no produce eventos de negocio, pero igual
declara un productor Kafka porque el `DeadLetterPublishingRecoverer` lo
necesita para publicar en la DLT.

---

## Idempotencia: por qué es distinta de audit

`order_summary` tiene una fila por `ordenId`, no una por evento. Cada evento
la actualiza, así que la protección no puede ser "ya existe esta clave
primaria" como en audit. En su lugar, `OrderSummaryService.aplicar` guarda
`ultimoEventoOccurredAt` y solo aplica un evento si su `occurredAt` es
**estrictamente posterior** al ya guardado:

```java
if (resumen.getUltimoEventoOccurredAt() != null
        && !evento.occurredAt().isAfter(resumen.getUltimoEventoOccurredAt())) {
    // se ignora
}
```

Esto cubre dos casos con la misma condición: un evento genuinamente más viejo
(desorden), y el reenvío exacto del mismo evento (entrega al menos una vez de
Kafka) — ambos fallan la comparación `isAfter`.

La clave del mensaje es `ordenId`, así que todos los eventos de una orden
caen en la misma partición y un solo hilo los procesa en el orden en que
Kafka los entregó; no hay carrera posible entre eventos de la misma orden
dentro de esta instancia.

### El segundo consumidor (`audit_trail_event`): contar sin duplicar

Este consumidor no necesita guardar el contenido del evento, solo saber
cuántos existen. La tabla `audit_trail_event` usa el mismo patrón que
`audit_events` en `ms-digitalfix-audit`: `eventId` como clave primaria.
`AuditTrailService.registrar` hace `existsById` antes de guardar (cubre el
reenvío normal) y además atrapa `DataIntegrityViolationException` al
guardar (cubre la carrera residual si dos hilos procesaran el mismo
`eventId`, aunque con un solo listener por partición no debería pasar).
`total()` es simplemente `repository.count()`.

---

## Estructura

```
config/
  DigitalfixKafkaProperties.java   topico y ajustes de reintento, desde application.yaml
  KafkaConsumerConfig.java         consumer factory, error handler (backoff + DLT)
  KafkaProducerConfig.java         producer factory y KafkaTemplate (solo para la DLT)
  JacksonConfig.java                ObjectMapper explicito (JavaTimeModule, tolera campos nuevos)
  SecurityConfig.java              JWT, audiencia, CORS
messaging/
  WorkOrderEvent.java              evento consumido (contrato con workorders)
  WorkOrderEventParser.java        valida y convierte el payload crudo
  AuditRecordEvent.java           evento consumido (contrato con audit)
  AuditRecordEventParser.java      valida y convierte el payload crudo
  consumer/
    WorkOrderEventConsumer.java    grupo report-metrics
    AuditTrailConsumer.java        grupo report-audit-trail
model/
  OrderSummaryEntity.java          una fila por orden, no un historial
  AuditTrailEventEntity.java       eventId como clave primaria, solo para contar
repository/
  OrderSummaryRepository.java
  AuditTrailEventRepository.java
service/
  OrderSummaryService.java         aplicar(), resumen(), certificado()
  AuditTrailService.java           registrar(), total()
dto/
  ReportSummaryView.java
  CertificateView.java
controller/
  ReportController.java           /api/v1/reports/summary, /api/v1/reports/orders/{id}/certificate
  HealthController.java           /api/health (publico)
exception/
  ErrorRecuperableException.java
  ErrorNoRecuperableException.java
  OrdenNoCerradaException.java    409 cuando se pide el certificado de una orden no cerrada
```

---

## API

Roles `Admin`, `Supervisor` o `Auditor`, scope `OT.Read`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/reports/summary` | Órdenes por estado, total, y promedio de horas hasta el cierre (solo entre las `CERRADA`) |
| GET | `/api/v1/reports/orders/{ordenId}/certificate` | Datos del certificado de una orden `CERRADA`; 404 si la orden no existe, 409 si existe pero no está cerrada |
| GET | `/api/health` | Público |

Ejemplo de `/api/v1/reports/summary`:

```json
{
  "ordenesPorEstado": { "INGRESADA": 2, "ASIGNADA": 1, "CERRADA": 5 },
  "total": 8,
  "tiempoPromedioCierreHoras": 36.5,
  "totalEventosAuditoria": 23
}
```

`tiempoPromedioCierreHoras` es `null` si todavía no hay ninguna orden cerrada.
`totalEventosAuditoria` viene del segundo consumidor (`audit_trail_event`),
no de `order_summary`: es independiente del resto del resumen.

---

## Probarlo de punta a punta

Requiere el clúster de Kafka levantado (ver el repo `infra`):

```
docker compose -f docker-compose.kafka.yml --env-file .env up -d
```

```
./mvnw spring-boot:run
```

Queda en el puerto 8086. En Kafka UI (`http://localhost:8089`), producir en
`digitalfix.workorders.events` con clave `OT-1001`:

```json
{"eventId":"re-1","eventType":"ORDEN_CREADA","occurredAt":"2026-11-22T00:00:00Z","ordenId":"OT-1001","estadoNuevo":"INGRESADA","cliente":"Cliente Reporte"}
```

y después, con `occurredAt` posterior:

```json
{"eventId":"re-2","eventType":"ESTADO_CAMBIADO","occurredAt":"2026-11-22T01:00:00Z","ordenId":"OT-1001","estadoAnterior":"INGRESADA","estadoNuevo":"CERRADA","tecnicoAsignado":"Tecnico Reporte"}
```

El log muestra `orden OT-1001 actualizada a estado CERRADA`. Reenviar el
primer mensaje otra vez: el log muestra `no es mas nuevo que el ya aplicado,
se ignora` y no hay `UPDATE` en el SQL logueado.

Para ver los tres consumer groups avanzando por separado sobre los dos
tópicos (con `audit` corriendo a la vez):

```
docker exec digitalfix-kafka1 kafka-consumer-groups \
  --bootstrap-server kafka1:29092,kafka2:29092,kafka3:29092 \
  --describe --group report-metrics

docker exec digitalfix-kafka1 kafka-consumer-groups \
  --bootstrap-server kafka1:29092,kafka2:29092,kafka3:29092 \
  --describe --group audit-timeline

docker exec digitalfix-kafka1 kafka-consumer-groups \
  --bootstrap-server kafka1:29092,kafka2:29092,kafka3:29092 \
  --describe --group report-audit-trail
```

Para probar el segundo consumidor específicamente: con `audit` corriendo,
producir el mismo mensaje en `digitalfix.workorders.events` que en el
ejemplo de arriba. `audit` lo guarda y publica un registro en
`digitalfix.audit.events`; el log de `report` muestra
`[report] evento de auditoria ... registrado` y
`GET /api/v1/reports/summary` incrementa `totalEventosAuditoria` en 1.

---

## Pendiente

- Despliegue en AWS detrás del BFF, igual que `audit`.
- `ddl-auto: update` en vez de migraciones (igual que el resto de los
  microservicios del proyecto).
- No hay forma de corregir un resumen si workorders publica un evento con un
  error de negocio ya consolidado (por ejemplo, una orden marcada `CERRADA`
  por error): `report` solo refleja lo que recibe, no audita el contenido.

---

## Nota sobre Spring Boot 4 / Spring Kafka 4

Mismo detalle que en `ms-digitalfix-audit`: `ExponentialBackOffWithMaxRetries`
vive en `org.springframework.kafka.support`, no en `...listener`. El
`ObjectMapper` se declara a mano en `JacksonConfig` en vez de depender de la
autoconfiguración de Jackson.
