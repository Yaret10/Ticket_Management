# Verificaciones realizadas

Fechas: verificación inicial 2026-09-16; actualización de roles 2026-09-17. Entorno: Windows, Temurin 25.0.4.1 LTS, Maven Wrapper 3.9.16. El repositorio original se inspeccionó exclusivamente en lectura. No se conectó ni modificó una base de producción ni la base actual del usuario.

| Verificación | Resultado |
|---|---|
| Compilación Java/Spring Boot | Aprobada |
| Pruebas HTTP, dominio, almacenamiento y outbox | Batería completa de 19 pruebas Java aprobadas, incluyendo renovación concurrente y los cuatro roles |
| JavaScript con node:test | 4 pruebas aprobadas |
| Generación de configuración local | Claves RSA y contraseñas aleatorias generadas; secretos no impresos |
| docker compose config --quiet | Aprobada |
| OpenAPI generado desde la aplicación | Generado y probado; copia en docs/openapi.json |
| Empaquetado ejecutable | JAR Spring Boot generado en target |
| Configuración IntelliJ añadida posteriormente | XML validado y prueba dirigida de login aprobada con importación de `.env`; sin arranque real desde el IDE |
| Testcontainers con SQL Server | 19 pruebas aprobadas: 5 específicas y 14 HTTP heredadas |

Resultado de la actualización de roles: **38 pruebas Java aprobadas** (19 sobre H2/dominio/mocks y 19 con SQL Server), además de **4 pruebas JavaScript**. Maven `fmt:format verify` terminó con código 0. Las pruebas se ejecutaron en contenedores aislados y no aplicaron V2 a la base actual del usuario.

La cobertura adicional comprueba Jefe limitado a su área principal aun con relaciones antiguas extra, Gerente autorizado para dos áreas y rechazado en una tercera aunque sea su área principal, descarga de evidencias, aprobación, dashboard, exportación con solo filas autorizadas, falta de permisos de atención y validación de áreas al registrar usuarios. La migración conserva usuarios, áreas de gestión y roles delegables existentes.

## Cobertura ejecutada

ApplicationSecurityTest: login por DNI, mensajes uniformes, me, cookies HttpOnly/SameSite/path, ausencia de sesión, CSRF real en GET/cookie/cabecera, rechazo de CSRF falsificado, expiración JWT, firma manipulada, RS512 no permitido, issuer/audience incorrectos, refresh vencido, rotación, reutilización y revocación familiar, logout, recuperación pública de páginas, renderizado de todas las páginas y contrato OpenAPI.

Flujo PENDIENTE→APROBADO→ATENDIDO→CERRADO y alternativa RECHAZADO. Matriz de transiciones negativas, jefe de área equivocada, usuario con cargo engañoso sin roles, terceros sin acceso a detalles/evidencias, carga fuera de etapa, informe obligatorio, versiones obsoletas, registros de historial/outbox y límites de búsqueda/paginación/ordenamiento.

Permisos de dashboard y exportación, archivo Excel vacío para tercero, solo filas propias, fórmulas como celdas de texto, recorrido de 501 filas cruzando el límite del lote y agrupación de `2026-10-01T02:00Z` en septiembre de Lima. Archivos: contenido falso, PDF válido/falso, formato no permitido, UTF-8, traversal, ausencia de sobrescritura y headers de descarga.

OutboxWorkerTest: fallo del proveedor se reprograma y no se reenvía inmediatamente; quinto intento pasa a FAILED. JavaScript: CSRF en POST, renovación y un reintento, renovación compartida entre solicitudes concurrentes, límite después de un segundo 401, rechazo de destinos externos y renovación de me.

Las pruebas HTTP usan H2 únicamente en test y reemplazan LoginGuard y la ejecución programada del worker para aislar SQL específico. No sustituyen la verificación de SQL Server. No se utiliza H2 como base de la aplicación local ni de producción.

## Verificación de SQL Server

El primer intento del 2026-09-16 falló porque Docker no estaba disponible: **Could not find a valid Docker environment** y ausencia del pipe dockerDesktopLinuxEngine. El 2026-09-17 Docker ya estuvo disponible y se repitió la verificación en contenedores SQL Server 2022 aislados. La migración incremental de roles se ensayó en una base separada creada exclusivamente en el contenedor de pruebas, nunca en la base del usuario.

SqlServerIT aprobó cinco casos: Flyway V1/V2 y DDL validate, restricciones FK, 100 asignaciones concurrentes de secuencia, @Version ante escritura obsoleta, buckets compartidos de login, coordinación de dos workers con reintentos y preservación de usuarios/áreas/delegaciones al migrar roles. SqlServerWorkflowIT aprobó las catorce pruebas HTTP heredadas con SQL Server/Flyway. Hubo avisos de conexión durante la inicialización de contenedores y al terminar el contenedor de la primera clase; no hubo fallos de pruebas.

Para repetir con Docker Desktop funcionando:

```powershell
.\mvnw.cmd verify
```

CI hace esta misma verificación en Linux, además de comprobar formato y ejecutar las pruebas JS. El pipeline fue escrito, pero no ejecutado en GitHub en esta sesión.

## Otras limitaciones verificables

### Actualización posterior: visualización de evidencias

Se ejecutaron dos pruebas Java dirigidas: formatos PNG/JPEG/PDF/TXT, contenido y encabezados inline/UTF-8/no-store/nosniff/CSP, consulta HEAD sin cuerpo, permisos del jefe y rechazo a terceros, relación incorrecta ticket/evidencia (404), formato no permitido (415), descarga existente y generación de OpenAPI. Las dos pasaron después de ajustar HEAD para no enviar el contenido. No se repitió la batería SQL de roles: esta función reutiliza el servicio de descarga autorizado y no modifica tablas ni datos.

Las seis pruebas JavaScript pasaron: las cuatro anteriores y dos nuevas sobre apertura síncrona de pestaña, ausencia de opener, consulta HEAD, renovación del JWT, navegación después de autorización, cierre al recibir 403 y mensaje ante bloqueo de ventanas. Se comprobó también la sintaxis del módulo detail.js. No se realizó una revisión visual en navegador real; PDF depende de que el navegador tenga habilitado su visor nativo.

- No se construyó ni desplegó la imagen Docker de esta actualización sobre la base actual del usuario. Compose se validó y las pruebas SQL utilizaron contenedores aislados.
- No se envió correo real por Graph: falta la configuración externa del tenant y sus credenciales. El adaptador está implementado; el worker se verificó con proveedor simulado.
- Las páginas se renderizaron mediante MockMvc y se probó el módulo de solicitudes. No se hizo una revisión visual ni un recorrido en un navegador real.
- No se importaron datos/evidencias históricos; el DDL desplegado, zona de fechas antiguas, matriz de roles y acceso al recurso compartido requieren contraste previo al corte.
- El almacenamiento entregado es local o un volumen compartido. No se implementó un adaptador de objetos; la interfaz FileStorage admite incorporarlo.
- No se realizaron pruebas de carga, de caída de procesos durante envío/archivo ni de despliegue real con varias instancias. Sus garantías y efectos posibles se documentan en arquitectura y notificaciones.

No se afirma que el sistema esté validado para producción sin las comprobaciones del despliegue real, sus credenciales, almacenamiento y permisos organizativos.
