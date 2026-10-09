# ms-digitalfix-workorders

Microservicio de dominio de DigitalFix. Administra el ciclo de vida de las
órdenes de trabajo de mantención eléctrica: creación, asignación de cuadrilla,
cambio de estado y cierre.

Forma parte del caso semestral de DSY1107 (Desarrollo Cloud Native I).

---

## Lugar en la arquitectura

```
Angular (SPA) → AWS API Gateway → ms-digitalfix-bff → ms-digitalfix-workorders
      ↑                ↑                  ↑                      ↑
      └──────── Microsoft Entra ID (IDaaS) ──────────────────────┘
```

Este servicio **no se expone a internet**. Solo lo llama el BFF, que le propaga
el mismo `Authorization: Bearer <jwt>` que recibió del cliente.

Aun así valida el token por su cuenta. La razón es que confiar en que "alguien
arriba ya validó" deja el servicio abierto a cualquiera que alcance su puerto
dentro de la red. Cada capa valida: el Gateway, el BFF y este microservicio.

---

## Identidad

| Concepto | Valor |
|---|---|
| Tenant | `6e49fdba-8435-41ac-8074-7b1a04cfd543` (appdemo1945) |
| Registro de la API | `DigitalFix-API` (`13a645a9-ea0e-498e-96b8-55a3d56f10eb`) |
| Audiencia esperada | el GUID pelado, y también `api://<guid>` |
| Emisor esperado | `https://login.microsoftonline.com/<tenant>/v2.0` |
| Scopes | `OT.Read`, `OT.Create` |
| App Roles | `Admin`, `Supervisor`, `Cliente`, `Auditor` |

No se crea un App Registration propio para este microservicio: comparte el de la
API, porque el token que recibe es el mismo que emitió Entra ID para el cliente
original.

> La entrega final va contra el tenant `gozalo23`
> (`a6a0c27e-18a7-4209-bd1b-32338be5cb66`), con el registro de API
> `8e5e408b-c101-4bc1-a5d3-01f702720462`. Cambiar `issuer-uri` y `audiences` en
> `application.yaml` al integrar.

### Sobre la audiencia

Con `requestedAccessTokenVersion: 2` en el manifiesto del registro, el claim
`aud` trae el **Application ID pelado**, no el `api://<guid>`. Con v1 ocurre lo
contrario. El validador acepta ambas formas para no quedar atado a esa
configuración.

---

## Modelo

### Orden de trabajo

| Campo | Tipo | Nota |
|---|---|---|
| `id` | String | Formato `OT-0001` |
| `cliente` | String | Obligatorio |
| `descripcion` | String | Obligatorio, máx. 500 caracteres |
| `direccion` | String | Obligatorio |
| `estado` | EstadoOT | Arranca en `INGRESADA` |
| `tecnicoAsignado` | String | Nulo hasta que un Supervisor asigna |
| `creadaPor` | String | `sub` del token de quien la creó |
| `creadaEn` | LocalDateTime | |
| `actualizadaEn` | LocalDateTime | |

### Estados

```
INGRESADA → ASIGNADA → EN_EJECUCION → CERRADA
     ↓           ↓            ↓
         → ANULADA ←
```

Una orden `CERRADA` o `ANULADA` es terminal: no admite más transiciones.

---

## Endpoints

| Método | Ruta | Roles | Scope |
|---|---|---|---|
| GET | `/api/workorders` | los cuatro | `OT.Read` |
| GET | `/api/workorders/{id}` | los cuatro | `OT.Read` |
| POST | `/api/workorders` | Admin, Supervisor, Cliente | `OT.Create` |
| PATCH | `/api/workorders/{id}/estado` | Admin, Supervisor | `OT.Create` |
| DELETE | `/api/workorders/{id}` | Admin | `OT.Create` |
| GET | `/api/health` | público | — |

La autorización combina dos condiciones que son distintas entre sí:

- `hasAnyRole(...)` — **quién** es el usuario (App Role asignado en Entra ID)
- `hasAuthority('SCOPE_...')` — **qué** consintió la aplicación (scope delegado)

Ambas deben cumplirse. Un `Admin` cuyo token no traiga `OT.Create` tampoco puede
crear: es el principio de mínimo privilegio.

### Reglas de visibilidad

Un usuario cuyo único rol es `Cliente` recibe solo las órdenes cuyo `creadaPor`
coincide con el `sub` de su token. Los demás perfiles ven todo.

---

## Respuestas de error

Cuerpo uniforme en JSON, para que el consumidor nunca reciba HTML:

```json
{
  "timestamp": "2026-10-09T20:15:33.421",
  "status": 403,
  "error": "Forbidden",
  "message": "Permisos insuficientes para esta operacion",
  "path": "/api/workorders/OT-0001"
}
```

| Código | Cuándo |
|---|---|
| 400 | Validación de campos fallida, o transición de estado inválida |
| 401 | Token ausente, o inválido en firma, emisor, audiencia o vigencia |
| 403 | Token válido, pero el rol o el scope no alcanzan |
| 404 | La orden no existe |

Spring Security devuelve cuerpos vacíos por defecto en 401 y 403, así que hay
que registrar un `AuthenticationEntryPoint` y un `AccessDeniedHandler` propios.

---

## Stack

- Java 17
- Spring Boot 4.0.8
- Maven
- Package base `cl.duoc.ms_digitalfix_workorders`
- Dependencias: Web, OAuth2 Resource Server, Validation, Data JPA

---

## Estructura prevista

```
src/main/java/cl/duoc/ms_digitalfix_workorders/
├── config/
│   ├── SecurityConfig.java            cadena de filtros, CORS, método security
│   ├── SecurityProperties.java        audiencias y orígenes desde el yaml
│   ├── AudienceValidator.java         valida el claim "aud"
│   └── JwtAuthoritiesConverter.java   roles → ROLE_*, scp → SCOPE_*
├── controller/
│   ├── HealthController.java
│   └── WorkOrderController.java
├── dto/
│   ├── EstadoOT.java
│   ├── WorkOrder.java
│   ├── CrearWorkOrderRequest.java
│   └── CambiarEstadoRequest.java
├── entity/
│   └── WorkOrderEntity.java
├── repository/
│   └── WorkOrderRepository.java
├── service/
│   └── WorkOrderService.java
└── exception/
    ├── ApiError.java
    ├── NotFoundException.java
    ├── EstadoInvalidoException.java
    ├── JsonAuthenticationEntryPoint.java
    ├── JsonAccessDeniedHandler.java
    └── GlobalExceptionHandler.java
```

Las clases de `config/` y `exception/` son prácticamente idénticas a las del
BFF. Conviene copiarlas y cambiar solo el package.

---

## Configuración

`src/main/resources/application.yaml`:

```yaml
server:
  port: 8081

spring:
  application:
    name: ms-digitalfix-workorders

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://login.microsoftonline.com/6e49fdba-8435-41ac-8074-7b1a04cfd543/v2.0

digitalfix:
  security:
    audiences:
      - 13a645a9-ea0e-498e-96b8-55a3d56f10eb
      - api://13a645a9-ea0e-498e-96b8-55a3d56f10eb
  cors:
    allowed-origins:
      - http://localhost:8080
```

Puerto 8081 para no chocar con el BFF en desarrollo local.

CORS solo necesita permitir al BFF, no al frontend: el navegador nunca llama
directamente a este servicio.

---

## Ejecutar

```
./mvnw spring-boot:run
```

Queda en `http://localhost:8081`.

Prueba rápida sin token:

```
GET http://localhost:8081/api/health       → 200
GET http://localhost:8081/api/workorders   → 401
```

---

## Integración con el BFF

Hoy el BFF resuelve las órdenes con un `WorkOrderService` en memoria. Al
conectar este microservicio, esa clase pasa a ser un cliente HTTP que reenvía
las llamadas propagando el token recibido. La interfaz pública que consume
`WorkOrderController` no cambia.

El BFF necesitará una propiedad nueva apuntando a este servicio:

```yaml
digitalfix:
  servicios:
    workorders: http://localhost:8081
```

Y en producción, la IP privada de la instancia EC2 correspondiente.

---

## Pendiente

- Persistencia real. La primera versión puede arrancar en memoria para
  desbloquear al frontend, pero la rúbrica de EP1 pide integración con base de
  datos cloud "mediante entidades, repositorios y propiedades de conexión"
- Validación de transiciones de estado: hoy cualquier cambio es aceptado
- Publicar eventos hacia `ms-digitalfix-audit` cuando una orden cambia de estado
- Desplegar en EC2 como servicio systemd, igual que el BFF
```
