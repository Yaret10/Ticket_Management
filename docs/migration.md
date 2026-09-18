# Migración y reversión del legado

Esta entrega no ejecutó cambios ni exportaciones sobre producción. Los modelos originales se pudieron leer en la dependencia editable local, pero no se verificó el DDL de la base desplegada. El proceso siguiente debe ensayarse con una restauración y aprobarse con los responsables de datos y permisos antes de un corte.

## Inventario y transformación

### Actualización V2 de roles en la aplicación Java

La migración incremental V2 conserva usuarios e identificadores, tickets, historial, evidencias y contraseñas. Sustituye SOLICITANTE por EMPLEADO y agrega GERENTE. Una jefatura que gestionaba áreas externas pasa a GERENTE y conserva sus relaciones chief_areas; JEFE queda limitado al área principal. Revisar las asignaciones antes de desplegar. Se preserva la delegación anterior de gestión de varias áreas agregando GERENTE a quienes podían delegar JEFE.

Desplegar backend y frontend juntos; no modificar V1 ni su checksum. Para revertir después de asignar roles nuevos, detener las escrituras, respaldar y reconciliar GERENTE→JEFE y EMPLEADO→SOLICITANTE junto con sus asignaciones y delegaciones mediante otra migración controlada; un simple retorno al JAR anterior no reconoce los roles nuevos. Conservar las áreas del Gerente y revisar el alcance de los Jefes. No borrar volúmenes ni ejecutar clean sobre una base con datos.

Restaurar una copia de SQL Server y copiar las evidencias conservando carpetas/códigos. Ejecutar `scripts/migration/legacy-preflight.sql` con un usuario de solo lectura sobre esa copia. Comparar columnas, restricciones y Alembic con los modelos inspeccionados; cuantificar estados, huérfanos, DNI inválidos y longitudes. No exportar contraseñas a logs o planillas generales.

| Origen | Destino / tratamiento |
|---|---|
| USUARIO.id_usuario | app_users.id, mismo identificador |
| username | username; name inicialmente username, sin inventar nombres |
| codigo | employee_code, capacidad de 100 |
| dni | dni; corregir nulos, duplicados o no válidos antes de importar |
| area / area2 | Catálogo areas con equivalencias revisadas; área principal para Jefe y chief_areas para Gerente autorizado |
| cargo | position; roles desde una matriz aprobada, nunca por coincidencia de palabras |
| correo | email; verificar/actualizar; no sustituir correos reales silenciosamente |
| password | No copiar texto plano; estrategia de credenciales descrita abajo |
| id_req_er / codigo | tickets.id / code, conservar ambos aunque código no cumpla el nuevo patrón |
| nombre_equipo / usuario | equipment / pc_user |
| prioridad / estado | Enums normalizados ALTA/MEDIA/BAJA y las cinco etapas; aceptar nombre/valor antiguo solo tras revisión |
| descripcion / informe_tecnico | description / technical_report; no truncar |
| usuario_solicita_id / usuario_aprueba_id | requester_id / approver_id; preservar claves |
| fecha_registro / fecha_atencion | UTC después de determinar zona histórica |
| Archivos bajo código | attachment con mismo ticket, UUID nuevo, nombre, hash, tamaño, fecha y manifiesto origen→storage_key |

Text del legado no impone el límite de 4000 para descripción/informe o de 100 para usuario PC. Si la copia contiene datos más largos, preparar una migración Flyway adicional y ajustar DTOs antes de importar; no recortar. Campo technician_id y closed_at quedan NULL cuando el origen no tiene esa información. No reconstruir eventos históricos como si se conocieran sus actores: registrar un único evento de importación con cuenta técnica revisada, estado preservado y observación explícita de procedencia. Preservar los registros y referencias originales en el paquete de auditoría.

Los tickets capturan área al crear. Para importados usar la mejor área histórica disponible; si solo existe área actual del solicitante, declarar esa aproximación en el manifiesto y revisar el alcance de jefaturas. Tickets con solicitante NULL o usuario inexistente no se descartan: bloquear su importación hasta resolver y conservarlos en staging. ATENDIDO/RECHAZADO/CERRADO sin informe incumplen la nueva restricción; corregir con anotación auditada o adaptar la política de legado mediante una migración explícita, sin inventar informes técnicos.

## Credenciales

Preferencia: importar cuentas deshabilitadas y un hash BCrypt de una contraseña aleatoria distinta por usuario, luego restablecer en un procedimiento controlado de TI verificando identidad. No activar todas las cuentas con una contraseña compartida. El backend no tiene comparación de texto plano ni autorregistro de recuperación. Habilitar cuentas y reemplazar sus hashes únicamente mediante una operación de administración auditada; entregar contraseñas por un canal autorizado y pedir su sustitución organizativa. Para el administrador inicial puede utilizarse el perfil bootstrap descrito en README.

Alternativa temporal de transición: en un entorno de migración aislado, leer credenciales antiguas con acceso limitado, generar BCrypt coste 12 usando PasswordEncoder de Spring y almacenarlo sin conservar el texto plano en destino. Para usuarios con contraseñas que excedan los límites o de baja calidad, usar restablecimiento. Documentar y eliminar los materiales temporales con el procedimiento de retención autorizado. No transportar claves JWT ni tokens del sistema Flask; todos deben autenticarse de nuevo.

## Importación ensayada

1. Crear **otra base** vacía y ejecutar Flyway del nuevo proyecto; mantener origen/copia sin escrituras.
2. Cargar staging desde exportaciones controladas, con trazabilidad y conteos/hash de entrada. Resolver datos inconsistentes antes de escribir tablas finales.
3. Usar SET IDENTITY_INSERT ON de SQL Server para cargar identificadores existentes en app_users y tickets; una tabla a la vez, dentro de transacciones por lote. Cargar áreas, usuarios, roles autorizados, jefaturas y tickets en ese orden. No poblar refresh_tokens: ninguna sesión antigua se migra.
4. Volver a alinear identity con DBCC CHECKIDENT y arrancar ticket_code_seq por encima del mayor correlativo existente revisado. MAX solo se utiliza para verificar la migración offline/reseed, nunca para generar identificadores en ejecución normal. No reiniciar la secuencia mensualmente. SQL dinámico de reseed debe partir de números validados, no texto del usuario.
5. Copiar todos los archivos del inventario mediante nombres UUID con CREATE_NEW, calcular SHA-256 y cargar attachments manteniendo ticket_id. Conservar el nombre original saneado y registrar la ruta anterior en un manifiesto protegido. Se pueden conservar metadatos de archivos heredados mayores de 10 MB; el límite de nuevas cargas sigue en la aplicación. Analizar formatos no permitidos y sospechosos antes de habilitar su descarga; conservarlos en cuarentena con referencia en staging/manifiesto, sin eliminarlos ni publicarlos como estáticos.
6. Insertar destinatarios de notificaciones por área con configuración revisada. No generar correos históricos ni recrear trabajo de outbox. Habilitar correo solo después del corte.
7. Comparar conteos por usuario/estado, claves, códigos, referencias, tamaños y hashes; probar permisos con cuentas representativas y el flujo completo. Verificar timestamps de ambos lados del cambio de mes en Lima.

## Corte y reversión

Congelar escrituras en Flask; obtener copia final de DB y evidencias; importar diferencias o repetir importación ensayada; validar y cambiar el acceso al nuevo servicio. Mantener Flask y origen en modo solo lectura durante la ventana de aceptación, con backups verificados. No apuntar Flyway a la base histórica ni compartir tablas de escritura con Flask.

Antes de nuevas escrituras, la reversión consiste en restablecer el acceso a Flask y restaurar su copia confirmada, conservando el destino nuevo para análisis. Si ya hubo tickets/cambios/evidencias en Java, no revertir descartándolos: congelar Java, exportar y reconciliar el delta con responsables, incluyendo ids, estados y nuevos informes/evidencias. Mantener los correos ya aceptados como efectos externos irreversibles; no reenviarlos durante el rollback. Para cambios de esquema del sistema nuevo, preferir migraciones hacia adelante; un restore exige una estrategia que preserve todas las escrituras posteriores al backup.
