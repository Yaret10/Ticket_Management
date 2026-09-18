-- SOLO LECTURA. Ejecutar sobre una copia restaurada de la base original.
SET NOCOUNT ON;
SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH,IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME IN ('USUARIO','REQ_EQUIPOS_RED')
ORDER BY TABLE_NAME,ORDINAL_POSITION;
SELECT COUNT_BIG(*) AS usuarios,
 SUM(CASE WHEN dni IS NULL OR LEN(dni)<>8 OR dni LIKE '%[^0-9]%' THEN 1 ELSE 0 END) AS dni_a_revisar,
 SUM(CASE WHEN correo IS NULL OR LEN(LTRIM(RTRIM(correo)))=0 THEN 1 ELSE 0 END) AS sin_correo,
 SUM(CASE WHEN area IS NULL OR LEN(LTRIM(RTRIM(area)))=0 THEN 1 ELSE 0 END) AS sin_area
FROM USUARIO;
SELECT estado,prioridad,COUNT_BIG(*) AS cantidad FROM REQ_EQUIPOS_RED GROUP BY estado,prioridad;
SELECT COUNT_BIG(*) AS tickets_con_solicitante_invalido FROM REQ_EQUIPOS_RED t
LEFT JOIN USUARIO u ON u.id_usuario=t.usuario_solicita_id WHERE u.id_usuario IS NULL;
SELECT COUNT_BIG(*) AS tickets_con_aprobador_invalido FROM REQ_EQUIPOS_RED t
LEFT JOIN USUARIO u ON u.id_usuario=t.usuario_aprueba_id WHERE t.usuario_aprueba_id IS NOT NULL AND u.id_usuario IS NULL;
SELECT COUNT_BIG(*) AS textos_fuera_de_limite FROM REQ_EQUIPOS_RED
WHERE LEN(CONVERT(NVARCHAR(MAX),descripcion))>4000 OR LEN(CONVERT(NVARCHAR(MAX),informe_tecnico))>4000
OR LEN(CONVERT(NVARCHAR(MAX),usuario))>100;
SELECT COUNT_BIG(*) AS grupos_codigo_duplicado FROM
(SELECT codigo FROM REQ_EQUIPOS_RED GROUP BY codigo HAVING COUNT(*)>1) x;
SELECT COUNT_BIG(*) AS grupos_usuario_duplicado FROM
(SELECT username FROM USUARIO GROUP BY username HAVING COUNT(*)>1) x;
