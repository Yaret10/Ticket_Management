# Diagnóstico del original y decisiones

Referencia inspeccionada en modo lectura: `../App_Ticket`. Se revisaron API de auth, tickets y administración, servicios de tickets y correo, schemas, templates, CSS, módulos JavaScript, configuración, requirements y modelos de la dependencia editable en `env_ticket/src/db-schema/db_schema`. No se accedió a la base de producción ni a la carpeta compartida de evidencias, ni se leyeron secretos de `.env`.

## Datos comprobados

`USUARIO`: id_usuario, codigo (100), username (100), password (100), dni (10 nullable, índice único), area, area2, cargo y correo. El modelo no tiene `nombre`; el servicio utiliza username como alternativa.

`REQ_EQUIPOS_RED`: id_req_er, codigo único (25), usuario de PC de tipo Text, fecha_registro, nombre_equipo (20), prioridad, descripcion, informe_tecnico, fecha_atencion, estado, usuario_aprueba_id y usuario_solicita_id. El modelo enum declara las cinco etapas y relaciones con USUARIO. No hay técnico, cierre fechado ni historial completo verificable en ese modelo.

El esquema declara `seq_req_er` como default de código SQL. El servicio Flask sobrescribe ese código con `TCK-yyyyMM-id` después de flush. El truncamiento a cuatro dígitos en el default puede colisionar después de 9999; la nueva secuencia nunca trunca cifras y no se reinicia mensualmente.

La evidencia se encuentra por código en una ruta compartida; la descarga original entrega el primer archivo ordenado de la carpeta. La carga exige solicitante y PENDIENTE, pero no valida formato ni tamaño y puede sobrescribir nombres. La descarga tiene la autorización comentada. El controlador de detalle sí comprueba propios, TI y jefe del área mediante helpers; esas reglas dependen del cargo y no se aplican uniformemente a evidencias y dashboard.

El listado admite búsqueda por código, equipo y descripción, prioridad y estado; devuelve hasta 9999 elementos. Los jefes ven sus tickets y los de area/area2; TI ve todos según palabras del cargo/área. El dashboard tiene una regla distinta para TI y carga todos los tickets en memoria. Hay promedio de atención, distribuciones por estado, prioridad y mes y exportación a Excel.

Los correos de creación incluyen solicitante y destinatarios configurados por área. APROBADO notifica al solicitante y a TI (`default_cc`, sección TI y direcciones de entorno); las otras etapas notifican al solicitante. Existen adaptadores Outlook/Graph, con envío síncrono, errores absorbidos y logs que incluyen datos personales y respuestas del proveedor.

## Correcciones deliberadas

| Problema original | Implementación |
|---|---|
| Comparación de contraseñas en texto plano | BCrypt con coste 12; no se admite fallback de texto plano |
| Token JSON utilizado por scripts | Cookies HttpOnly, JWT RS256, rotación de refresh tokens y CSRF |
| Registro de pruebas disponible a cualquier usuario autenticado | Un solo registro, exige TI y permiso manageUsers explícito |
| Cargo con palabras TI/SISTEMAS produce privilegios | Roles normalizados y asignaciones explícitas |
| Jefatura limitada a area/area2 y comprobaciones inconsistentes | Jefe de área principal; Gerente con relación chief_areas sin límite artificial |
| Evidencias sin autorización y reglas distribuidas entre controladores | Política única aplicada antes de entregar datos |
| Visibilidad distinta en dashboard y Excel | TI ve todos para gestionar, jefe sus áreas + propios, solicitante propios |
| Evidencia arbitraria/sobrescrita | Tipos permitidos, verificación de contenido, UUID, CREATE_NEW y metadatos |
| Sin historial completo/concurrencia controlada | Historial transaccional y @Version; 409 en conflictos |
| Fallo de correo sin persistencia/reintentos | Outbox transaccional, reserva temporal y cinco intentos |

Se conservan identidad conTIgo/STROBBE, logo y azul `#2e6ca4`, tema claro/oscuro, formulario con equipo/usuario PC/descripción/prioridad y todo el flujo. Se añaden nombre explícito y correo al registro, imprescindible para notificar correctamente; se mantiene cargo como dato descriptivo y código del empleado. El área del ticket se captura al crear para que un traslado posterior del solicitante no cambie las autorizaciones históricas. La creación exige EMPLEADO; TI y JEFE pueden recibir también ese rol. El Excel conserva los campos originales, añade equipo, informe y fechas de etapas y elimina el truncamiento a 500 caracteres de la descripción. El correo nuevo resume etapa y código y dirige al detalle autenticado; conserva destinatarios y momentos de envío.

## Supuestos que requieren validación antes de migrar

Los modelos disponibles no garantizan que la base desplegada coincida con ellos. Los timestamps antiguos no incluyen zona: comprobar zona del servidor y distinguir los creados por `datetime.now()` de los defaults `now_lima()`. No asumir que todo usuario con cargo similar a TI debe recibir TI. No inferir técnicos, fecha de cierre o actores de etapas que no están almacenados. Corregir mediante revisión las credenciales, DNI no válidos, áreas equivalentes, descripciones que excedan límites y tickets huérfanos. No se ha importado información real.
