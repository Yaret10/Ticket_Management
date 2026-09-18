CREATE TABLE areas (id BIGINT IDENTITY(1,1) PRIMARY KEY, name NVARCHAR(120) NOT NULL UNIQUE);
CREATE TABLE app_users (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 dni VARCHAR(8) NOT NULL UNIQUE CHECK (LEN(dni)=8 AND dni NOT LIKE '%[^0-9]%'),
 username NVARCHAR(100) NOT NULL UNIQUE, name NVARCHAR(120) NOT NULL,
 password_hash VARCHAR(255) NOT NULL, email NVARCHAR(254) NOT NULL,
 position NVARCHAR(120) NOT NULL, employee_code NVARCHAR(100), enabled BIT NOT NULL DEFAULT 1,
 manage_users BIT NOT NULL DEFAULT 0, area_id BIGINT NOT NULL REFERENCES areas(id)
);
CREATE TABLE roles (name VARCHAR(20) PRIMARY KEY);
INSERT INTO roles(name) VALUES ('SOLICITANTE'),('JEFE'),('TI');
CREATE TABLE user_roles (user_id BIGINT NOT NULL REFERENCES app_users(id), role VARCHAR(20) NOT NULL REFERENCES roles(name), PRIMARY KEY(user_id,role));
CREATE TABLE user_grantable_roles (user_id BIGINT NOT NULL REFERENCES app_users(id), role VARCHAR(20) NOT NULL REFERENCES roles(name), PRIMARY KEY(user_id,role));
CREATE TABLE chief_areas (user_id BIGINT NOT NULL REFERENCES app_users(id), area_id BIGINT NOT NULL REFERENCES areas(id), PRIMARY KEY(user_id,area_id));
CREATE TABLE area_notification_recipients (area_id BIGINT NOT NULL REFERENCES areas(id), email NVARCHAR(254) NOT NULL, PRIMARY KEY(area_id,email));
CREATE SEQUENCE ticket_code_seq AS BIGINT START WITH 1 INCREMENT BY 1;
CREATE TABLE tickets (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 code VARCHAR(40) NOT NULL UNIQUE, equipment NVARCHAR(120) NOT NULL, pc_user NVARCHAR(100) NOT NULL,
 description NVARCHAR(4000) NOT NULL, priority VARCHAR(10) NOT NULL CHECK(priority IN ('ALTA','MEDIA','BAJA')),
 state VARCHAR(20) NOT NULL CHECK(state IN ('PENDIENTE','APROBADO','ATENDIDO','RECHAZADO','CERRADO')),
 requester_id BIGINT NOT NULL REFERENCES app_users(id), area_id BIGINT NOT NULL REFERENCES areas(id),
 approver_id BIGINT REFERENCES app_users(id), technician_id BIGINT REFERENCES app_users(id), technical_report NVARCHAR(4000),
 created_at DATETIME2(6) NOT NULL, attended_at DATETIME2(6), closed_at DATETIME2(6),
 CONSTRAINT ck_ticket_report CHECK(state NOT IN ('ATENDIDO','RECHAZADO','CERRADO') OR LEN(LTRIM(RTRIM(technical_report)))>0 AND technical_report IS NOT NULL)
);
CREATE INDEX ix_tickets_requester_date ON tickets(requester_id,created_at DESC,id);
CREATE INDEX ix_tickets_area_date ON tickets(area_id,created_at DESC,id);
CREATE INDEX ix_tickets_state_priority_date ON tickets(state,priority,created_at DESC,id);
CREATE TABLE ticket_history (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, ticket_id BIGINT NOT NULL REFERENCES tickets(id),
 actor_id BIGINT NOT NULL REFERENCES app_users(id), previous_state VARCHAR(20) CHECK(previous_state IN ('PENDIENTE','APROBADO','ATENDIDO','RECHAZADO','CERRADO')),
 new_state VARCHAR(20) NOT NULL CHECK(new_state IN ('PENDIENTE','APROBADO','ATENDIDO','RECHAZADO','CERRADO')), changed_at DATETIME2(6) NOT NULL, observation NVARCHAR(4000)
);
CREATE INDEX ix_history_ticket ON ticket_history(ticket_id,id);
CREATE TABLE attachments (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, ticket_id BIGINT NOT NULL REFERENCES tickets(id), uploaded_by BIGINT NOT NULL REFERENCES app_users(id),
 storage_key VARCHAR(36) NOT NULL UNIQUE, original_name NVARCHAR(150) NOT NULL, content_type VARCHAR(100) NOT NULL,
 size BIGINT NOT NULL CHECK(size>0), sha256 VARCHAR(64) NOT NULL, created_at DATETIME2(6) NOT NULL
);
CREATE INDEX ix_attachments_ticket ON attachments(ticket_id,id);
CREATE TABLE refresh_families (
 id VARCHAR(36) PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 user_id BIGINT NOT NULL REFERENCES app_users(id), created_at DATETIME2(6) NOT NULL,
 expires_at DATETIME2(6) NOT NULL, revoked BIT NOT NULL DEFAULT 0
);
CREATE INDEX ix_family_user_expiry ON refresh_families(user_id,expires_at);
CREATE TABLE refresh_tokens (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
 token_hash VARCHAR(64) NOT NULL UNIQUE, family VARCHAR(36) NOT NULL REFERENCES refresh_families(id), user_id BIGINT NOT NULL REFERENCES app_users(id),
 expires_at DATETIME2(6) NOT NULL, used_at DATETIME2(6), revoked BIT NOT NULL DEFAULT 0
);
CREATE INDEX ix_refresh_family ON refresh_tokens(family);
CREATE INDEX ix_refresh_user_expiry ON refresh_tokens(user_id,expires_at);
CREATE TABLE login_attempts (bucket VARCHAR(64) PRIMARY KEY, attempts INT NOT NULL, window_start DATETIME2(6) NOT NULL);
CREATE INDEX ix_login_window ON login_attempts(window_start);
CREATE TABLE notification_outbox (
 id BIGINT IDENTITY(1,1) PRIMARY KEY, recipient NVARCHAR(254) NOT NULL, subject NVARCHAR(200) NOT NULL, body NVARCHAR(4000) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','PROCESSING','SENT','FAILED')), attempts INT NOT NULL DEFAULT 0,
 next_attempt DATETIME2(6) NOT NULL, lease_until DATETIME2(6), lease_owner VARCHAR(36), last_error NVARCHAR(200), created_at DATETIME2(6) NOT NULL, sent_at DATETIME2(6)
);
CREATE INDEX ix_outbox_due ON notification_outbox(status,next_attempt,lease_until) INCLUDE(attempts);
