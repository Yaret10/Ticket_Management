# Microsoft Graph y notificaciones

La operación de ticket y las filas notification_outbox se confirman en la misma transacción. Un fallo de Graph posterior no revierte el ticket. Hay un mensaje por destinatario para evitar que el fallo de una dirección obligue a reenviar a todos.

## Destinatarios

- Creación: solicitante y emails de area_notification_recipients para el área capturada en el ticket.
- APROBADO: solicitante, usuarios habilitados con rol TI, `NOTIFY_TI` y `NOTIFY_DEFAULT_CC` separados por comas. Conserva el alcance del original; default_cc y las cuatro variables personales originales se consolidan en configuración sin nombres personales.
- ATENDIDO, RECHAZADO y CERRADO: solicitante.

Importar `notificaciones_jefes.json` del original como filas por área, con revisión de las direcciones. No se copian datos personales reales al repositorio. Las áreas del ejemplo contienen un jefe ficticio. Los jefes autorizados para aprobar y los destinatarios de correo son configuraciones distintas, como en el original: recibir correo no otorga permisos.

## Configurar Entra ID

1. Registrar una aplicación en el tenant de Microsoft Entra ID.
2. Añadir permiso de **aplicación** Microsoft Graph `Mail.Send`, con consentimiento del administrador.
3. Restringir los buzones permitidos mediante las políticas de acceso de aplicaciones o RBAC de Exchange que utilice la organización.
4. Crear una credencial y suministrarla desde un gestor de secretos. Configurar GRAPH_TENANT_ID, GRAPH_CLIENT_ID, GRAPH_CLIENT_SECRET y GRAPH_SENDER_EMAIL. No almacenarlos en archivos versionados.
5. Activar MAIL_MODE=graph y APP_PUBLIC_URL con URL HTTPS de la aplicación.

El adaptador usa client_credentials, scope `https://graph.microsoft.com/.default`, y POST `/v1.0/users/{sender}/sendMail`, con cuerpo de texto y `saveToSentItems=true`. Timeout de conexión 10 segundos y lectura 30 segundos. El token Graph es interno al adaptador; nunca se entrega al navegador. Se obtiene para cada mensaje; para cargas mayores podría añadirse una caché de credenciales del proveedor sin cambiar los casos de uso. Referencia: [sendMail oficial](https://learn.microsoft.com/en-us/graph/api/user-sendmail?view=graph-rest-1.0).

## Reservas, reintentos y garantías

Cada 5 segundos el worker procesa hasta 20 registros. UPDATE TOP(1), UPDLOCK, READPAST y OUTPUT reservan atómicamente un registro con lease_owner aleatorio y lease_until de 120 segundos. No se mantiene una transacción abierta durante la llamada HTTP. Instancias distintas no procesan simultáneamente un lease vigente. La base configurada usa READ COMMITTED sin READ_COMMITTED_SNAPSHOT.

Hasta cinco intentos; esperas 30, 60, 120 y 240 segundos. Después se marca FAILED. Una caída durante envío deja PROCESSING; al vencer la reserva puede retomarse. Si se agotaron intentos al caer el proceso, se marca FAILED en el siguiente ciclo. El último error conserva únicamente el nombre de la excepción. Supervisar pendientes/FAILED y reprogramarlos de forma controlada después de resolver el proveedor; nunca reiniciar intentos automáticamente sin revisión.

La persistencia evita perder el trabajo antes del envío; la entrega es **al menos una vez cuando los reintentos tienen éxito**, con posibles duplicados. Una caída después de que Graph acepte el correo pero antes de marcar SENT puede provocar reenvío. Graph 202 significa aceptación, no confirmación de recepción del destinatario. No se promete exactamente una vez ni entrega final garantizada tras cinco fallos.

MAIL_MODE=local usa LocalMailGateway y marca la simulación como SENT. No hace conexiones de correo reales y no imprime emails ni cuerpo. Nunca ejecuta Outlook/COM.
