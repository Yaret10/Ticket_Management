# conTIgo

Web application for registering and managing support tickets. It uses Java 25, Spring Boot, Thymeleaf, JavaScript, Spring Security, and SQL Server.

## Requirements

- JDK 25.
- An accessible SQL Server or Azure SQL instance.

## Run locally

For local SQL Server, your `.env` only needs `DB_PASSWORD`; the default host is
`localhost:1433`, the default user is `sa`, and the local RSA files in `.local/` are used.
Set `DB_URL` and `DB_USER` only if your SQL Server uses a different host, port, or user. Then run:

```powershell
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080/login`. The SQL Server database must already exist and be named `contigo`; Flyway creates and upgrades its schema at startup.

## Tests

```powershell
.\mvnw.cmd test
node --test scripts/frontend-tests.mjs
```

## Azure App Service deployment

The workflow `.github/workflows/main_contigo-backend.yml` builds and deploys an executable JAR to `contigo-backend`. It does not use container images or companion services.

Configure the App Service as **Linux / Java SE / Java 25** with this startup command:

```text
java -jar /home/site/wwwroot/app.jar --server.port=8080
```

In **Environment variables**, create the values from `.env.example`. The required settings are `SPRING_PROFILES_ACTIVE=prod`, `WEBSITES_PORT=8080`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, `ATTACHMENTS_ROOT=/home/data/evidence`, and `APP_PUBLIC_URL` with the real HTTPS domain. The `prod` profile intentionally has no local defaults.

JWT values can be full PEM text using line breaks or literal `\\n`. Do not add keys or passwords to Git.

Azure SQL must allow the App Service outbound addresses, or be reached through private networking. The database identity must have enough permissions to execute Flyway migrations.

To create the first administrator in an empty database, temporarily set `SPRING_PROFILES_ACTIVE=prod,bootstrap` and define `BOOTSTRAP_DNI`, `BOOTSTRAP_NAME`, `BOOTSTRAP_EMAIL`, and `BOOTSTRAP_PASSWORD`. The app creates the account and exits; restore the profile to `prod` and restart.

The API is under `/api/v1`. Open the application at `/login`; `/` requires authentication.

## Additional documentation

- [API](docs/api.md)
- [Architecture](docs/architecture.md)
- [Security](docs/security.md)
- [Migration](docs/migration.md)
