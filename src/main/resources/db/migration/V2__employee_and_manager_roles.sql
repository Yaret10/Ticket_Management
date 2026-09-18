-- Keep existing users, grants and ticket relationships; never edit the applied V1 migration.
INSERT INTO roles(name) VALUES ('EMPLEADO'), ('GERENTE');

INSERT INTO user_roles(user_id,role)
SELECT user_id,'EMPLEADO' FROM user_roles WHERE role='SOLICITANTE';
INSERT INTO user_grantable_roles(user_id,role)
SELECT user_id,'EMPLEADO' FROM user_grantable_roles WHERE role='SOLICITANTE';
DELETE FROM user_roles WHERE role='SOLICITANTE';
DELETE FROM user_grantable_roles WHERE role='SOLICITANTE';
DELETE FROM roles WHERE name='SOLICITANTE';

-- Old chiefs managing other areas keep their previous scope as managers.
INSERT INTO user_roles(user_id,role)
SELECT r.user_id,'GERENTE' FROM user_roles r JOIN app_users u ON u.id=r.user_id
WHERE r.role='JEFE' AND EXISTS
  (SELECT 1 FROM chief_areas a WHERE a.user_id=u.id AND a.area_id<>u.area_id);
DELETE FROM user_roles WHERE role='JEFE' AND user_id IN
  (SELECT user_id FROM user_roles WHERE role='GERENTE');
-- Granting JEFE previously included multiple managed areas, now represented by GERENTE.
INSERT INTO user_grantable_roles(user_id,role)
SELECT user_id,'GERENTE' FROM user_grantable_roles WHERE role='JEFE';
