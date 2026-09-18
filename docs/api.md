# API REST v1

El contrato OpenAPI vivo está en `/v3/api-docs`, detrás del mismo JWT que las páginas. Swagger UI utiliza las cookies de la sesión del navegador. Habilita soporte de CSRF; obtener primero el token desde `/api/v1/auth/csrf`. En prod se desactiva documentación pública. Los endpoints de auth distintos de me no necesitan access token, pero todos sus POST necesitan CSRF.

| Método / ruta bajo /api/v1 | Petición / resultado |
|---|---|
| GET /auth/csrf | `{token, headerName}` y cookie XSRF-TOKEN |
| POST /auth/login | DNI de 8 dígitos, password; cookies y mensaje |
| POST /auth/refresh | Cookie REFRESH; rotación y nuevas cookies |
| POST /auth/logout | Revoca familia presentada y elimina cookies |
| GET /auth/me | UserView: identidad, área, cargo, código y permisos |
| POST /users | Registro autorizado; UserView, 201 |
| GET /areas | Áreas para el formulario |
| POST /tickets | equipo, usuario PC, descripción y prioridad; 201 |
| GET /tickets | q, state, priority, page, size, sort, ascending |
| GET /tickets/{id} | TicketView con version y datos de etapas |
| GET /tickets/{id}/history | Actor, fecha, estado previo/nuevo, observación |
| POST /tickets/{id}/approve | `{version, observation}` |
| POST /tickets/{id}/attend | `{version, observation}`; informe obligatorio |
| POST /tickets/{id}/reject | `{version, observation}`; motivo obligatorio |
| POST /tickets/{id}/close | `{version, observation}` |
| GET /tickets/{id}/attachments | Lista de metadatos |
| POST /tickets/{id}/attachments | multipart/form-data, parte `file`; 201 |
| GET /tickets/{id}/attachments/{attachmentId} | Descarga autorizada |
| GET /tickets/{id}/attachments/{attachmentId}/view | Visualización autorizada inline: PNG, JPEG, PDF o TXT UTF-8; HEAD disponible para verificar acceso |
| GET /reports/dashboard | Total, estado, prioridad, meses y promedio de atención |
| GET /reports/excel | q, state, priority; XLSX streaming por id ascendente |

## Ejemplos

Registro de ticket, sin solicitante enviado por el cliente:

```json
{"equipment":"PC-DEMO-01","pcUser":"usuario.pc","description":"No tiene conexión a la red","priority":"ALTA"}
```

Aprobación con la versión obtenida del detalle:

```json
{"version":0,"observation":"Solicitud autorizada"}
```

Registro de usuario, exige roles incluidos en grantableRoles del creador:

```json
{"dni":"00000100","username":"usuario.demo","name":"Persona de prueba","password":"Una-clave-ficticia-123!","email":"usuario@example.invalid","areaId":1,"position":"Asistente","employeeCode":"DEMO-100","roles":["EMPLEADO"],"managedAreas":[]}
```

Usar Fetch con `credentials: 'same-origin'`. El módulo `api.js` obtiene el CSRF y renueva autenticación. La API no admite bearer enviado por cabecera como alternativa a la cookie.

Listado: `page` empieza en 0, `size` entre 1 y 100 (default 20), `page` máximo 100000. Respuesta: `content`, `totalElements`, `totalPages`, `number`, `size`. `sort`: createdAt, code, priority, state; desempate por id. `ascending` default false. `q` máximo 200, sin interpretación de comodines del usuario. Estado y prioridad son enums en mayúsculas. Exportación usa filtros equivalentes; su orden es id ascendente para permitir recorrido estable por cursor.

UTC en JSON ISO-8601; el frontend presenta Lima. El dashboard agrupa meses en Lima. El promedio mide registro a atención, incluye las fechas de rechazo del modelo original y conserva fracción de día, redondeada a un decimal.

## Errores

| HTTP | Significado |
|---|---|
| 400 | Datos, filtros o informe inválidos |
| 401 | No autenticado, firma/claims inválidas, expiración, renovación/replay inválido |
| 403 | Permiso insuficiente o CSRF incorrecto/ausente |
| 404 | Recurso inexistente o evidencia sin relación con el ticket |
| 409 | Estado inválido, versión cambiada o restricción única |
| 413 | Evidencia demasiado grande |
| 415 | Formato no compatible con visualización; usar descarga |
| 429 | Ventana de intentos excedida |
| 500 | Error interno con detalle comprensible y sin excepción técnica |
| 503 | Capacidad de exportaciones ocupada |

ProblemDetail contiene status y detail; errores de validación añaden `errors`. El header X-Correlation-ID permite correlacionar el incidente sin incluir datos personales. Para una petición insegura sin JWT ni CSRF, el filtro CSRF puede devolver 403 antes de evaluar autenticación; con CSRF válido y sin autenticación devuelve 401. Las páginas privadas sin JWT válido redirigen a recuperación; la API devuelve 401.
