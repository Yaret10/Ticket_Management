# conTIgo

Aplicación web para registrar y gestionar tickets de soporte. Está construida con Java 25, Spring Boot, Thymeleaf, JavaScript, Spring Security, SQL Server y Docker.

## Requisitos

- JDK 25.
- Docker Desktop.
- Git (opcional, para control de versiones).

## Ejecutar con Docker

Desde la carpeta del proyecto:

```powershell
java scripts/GenerateLocalConfig.java
docker compose up -d --build
```

Abrir `http://localhost:8080/login`.

La base de datos es SQL Server y se llama `contigo`. Docker guarda los datos en los volúmenes `contigo_sql-data` y `contigo_evidence`.

Para detener los contenedores sin borrar datos:

```powershell
docker compose down
```

No uses `docker compose down -v` si quieres conservar la base de datos y las evidencias.


## Usuarios de desarrollo

La contraseña común está en `DEV_PASSWORD` dentro de `.env`.

| DNI | Rol | Función |
|---|---|---|
| `00000001` | EMPLEADO | Crea y consulta sus tickets |
| `00000002` | JEFE | Gestiona su área principal |
| `00000003` | TI | Atiende, rechaza y administra usuarios |
| `00000004` | EMPLEADO | Usuario de otra área |
| `00000005` | GERENTE | Gestiona las áreas asignadas |

Los usuarios de prueba solo se crean en una base vacía cuando `DEV_SEED=true`.

## Flujo de un ticket

```text
EMPLEADO crea el ticket
        ↓
JEFE o GERENTE aprueba
        ↓
TI atiende o rechaza
        ↓
EMPLEADO cierra el ticket atendido
```

Las evidencias admiten PDF, PNG, JPG/JPEG y TXT UTF-8, hasta 10 MB. En el detalle se pueden visualizar en el navegador o descargar.

## Pruebas

```powershell
.\mvnw.cmd test
node --test scripts/frontend-tests.mjs
```

Las pruebas de SQL Server usan Testcontainers y requieren Docker disponible:

```powershell
.\mvnw.cmd verify
```

## Configuración y seguridad

`.env.example` es solo una plantilla. El archivo `.env` contiene la configuración local y no debe subirse a Git. Las claves JWT se generan en `.local/`; esa carpeta también está excluida por `.gitignore`.

La autenticación usa JWT en cookies HttpOnly, refresh tokens opacos, CSRF y contraseñas protegidas con BCrypt.

La API está disponible bajo `/api/v1`. Swagger UI se encuentra en `/swagger-ui/index.html` después de iniciar sesión.

## Documentación adicional

- [API](docs/api.md)
- [Arquitectura](docs/architecture.md)
- [Permisos y seguridad](docs/security.md)
- [Migración](docs/migration.md)
- [Pruebas y limitaciones](docs/verification.md)
