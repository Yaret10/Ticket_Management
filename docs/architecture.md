# Arquitectura y modelo de datos

Una aplicación MVC y una base SQL Server. Los módulos se organizan por funcionalidad; controladores API pequeños delegan en servicios transaccionales. Los records representan DTOs, no entidades serializadas. TicketPolicy concentra visibilidad y transiciones. No se incorporaron Redis, Kafka ni infraestructura distribuida adicional: SQL Server coordina códigos, renovación y outbox.

```mermaid
flowchart LR
  Browser[Thymeleaf + CSS + módulos JS] --> Security[Security Resource Server / Cookie JWT + CSRF]
  Security --> API[API /api/v1]
  API --> Auth[auth]
  API --> Users[users + areas]
  API --> Tickets[tickets + TicketPolicy]
  API --> Files[attachments]
  API --> Reports[reports]
  Auth --> DB[(SQL Server / Flyway)]
  Users --> DB
  Tickets --> DB
  Tickets --> Outbox[notifications / Outbox]
  Outbox --> DB
  Files --> DB
  Files --> Storage[FileStorage / disco o volumen compartido]
  Reports --> DB
  Worker[Worker programado / lease] --> DB
  Worker --> Gateway[MailGateway]
  Gateway --> Graph[Microsoft Graph]
  Gateway --> Local[Simulación local]
```

```mermaid
erDiagram
  AREAS ||--o{ APP_USERS : principal
  APP_USERS ||--o{ USER_ROLES : roles
  ROLES ||--o{ USER_ROLES : define
  APP_USERS ||--o{ USER_GRANTABLE_ROLES : delega
  ROLES ||--o{ USER_GRANTABLE_ROLES : define
  APP_USERS ||--o{ CHIEF_AREAS : gerente_gestiona
  AREAS ||--o{ CHIEF_AREAS : autorizada
  AREAS ||--o{ AREA_NOTIFICATION_RECIPIENTS : destinatarios
  APP_USERS ||--o{ TICKETS : solicita
  AREAS ||--o{ TICKETS : area_capturada
  APP_USERS ||--o{ TICKETS : aprueba_o_atiende
  TICKETS ||--o{ TICKET_HISTORY : cambios
  APP_USERS ||--o{ TICKET_HISTORY : actor
  TICKETS ||--o{ ATTACHMENTS : evidencias
  APP_USERS ||--o{ ATTACHMENTS : sube
  APP_USERS ||--o{ REFRESH_FAMILIES : sesiones
  REFRESH_FAMILIES ||--o{ REFRESH_TOKENS : rotaciones
  APP_USERS ||--o{ REFRESH_TOKENS : propietario
  APP_USERS {
    bigint id PK
    varchar dni UK
    varchar username UK
    varchar password_hash
    bit manage_users
    bigint version
  }
  TICKETS {
    bigint id PK
    varchar code UK
    bigint requester_id FK
    bigint area_id FK
    varchar state
    varchar priority
    bigint version
    datetime created_at
  }
  REFRESH_FAMILIES {
    varchar id PK
    bigint user_id FK
    bigint version
    datetime expires_at
    bit revoked
  }
  REFRESH_TOKENS {
    bigint id PK
    varchar token_hash UK
    varchar family FK
    datetime expires_at
    datetime used_at
    bit revoked
  }
  NOTIFICATION_OUTBOX {
    bigint id PK
    varchar status
    int attempts
    datetime next_attempt
    datetime lease_until
    varchar lease_owner
  }
```

El outbox almacena un mensaje independiente por destinatario; no necesita una relación con tickets para conservar el trabajo pendiente tras modificaciones de datos. Los buckets del login contienen hashes SHA-256 de DNI/IP y una ventana, no sus valores originales. Las entidades mutables solo se usan dentro del backend; el acceso a asociaciones utiliza métodos para permitir inicialización de proxies JPA.

Las consultas paginadas utilizan EntityGraph para relaciones de ticket, solicitante, área, aprobador y técnico. Las distribuciones del dashboard se agregan en SQL. El promedio utiliza proyecciones de fechas e identificadores por lotes de hasta 500, sin cargar entidades completas. Los reportes recorren lotes de 500 con cursor por id y límite superior inicial, sin repetir COUNT del resultado total; SXSSF mantiene 100 filas en memoria y abre hojas adicionales al alcanzar el límite de Excel. No hay snapshot transaccional global de la exportación: cambios durante el recorrido pueden verse en lotes posteriores.

La secuencia `ticket_code_seq` asigna correlativos globales independientes de identity. El formato toma el mes en Lima y admite más de cuatro dígitos; puede haber saltos por rollback. `@Version` protege transiciones. La carga de evidencia fuerza incremento optimista de versión para impedir que una aprobación concurrente y la carga se confirmen sobre la misma versión.

Las escrituras de archivos no pueden incluirse en una transacción SQL: un rollback borra el archivo mediante synchronization; una caída del proceso puede dejar un archivo huérfano. Reconciliar periódicamente storage_key contra el almacenamiento, preservando archivos recientes que aún puedan estar en transacción. No eliminar archivos automáticamente durante una migración.

Para varias instancias: claves RSA compartidas desde un gestor de secretos, misma base, sincronización de reloj, mismo almacenamiento persistente de evidencias y un proxy HTTPS de confianza. FileStorage permite un adaptador de objetos futuro; la implementación entregada usa un volumen local/compartido. No usar discos efímeros independientes por réplica. Los leases de correo se coordinan en SQL; no se requiere afinidad de sesiones.

El proceso JVM establece UTC desde el arranque para que los Timestamp de JDBC no dependan de la zona del servidor; Hibernate usa también UTC explícitamente. La presentación conserva America/Lima. Las exportaciones tienen un executor limitado a cuatro hilos y ocho tareas en espera, con timeout configurable app.reports.timeout-ms (cinco minutos por defecto); la saturación devuelve 503. Dimensionar disco temporal y memoria según la carga de SXSSF.
