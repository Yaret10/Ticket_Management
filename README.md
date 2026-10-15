# conTIgo

Web application for registering and managing support tickets. It uses Java 25, Spring Boot, Angular, Spring Security, and SQL Server. Angular and the API are served from the same Spring Boot JAR and Azure App Service.

## Requirements

- JDK 25.
- Node.js 24 and npm.
- An accessible SQL Server or Azure SQL instance.

## Run locally

For local SQL Server, your `.env` only needs `DB_PASSWORD`; the default host is
`localhost:1433`, the default user is `sa`, and the local RSA files in `.local/` are used.
Set `DB_URL` and `DB_USER` only if your SQL Server uses a different host, port, or user. Build Angular and run Spring Boot:

```powershell
cd frontend
npm ci
npm run build
cd ..
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080/login`. The SQL Server database must already exist and be named `contigo`; Flyway creates and upgrades its schema at startup.

## Tests

```powershell
cd frontend
npm ci
npm run build
cd ..
.\mvnw.cmd test
```

For frontend development, run Spring Boot on port 8080 and `npm start` in `frontend/` on port 4200. The Angular dev server proxies `/api` to Spring Boot. For production, Maven copies `frontend/dist` into the JAR's static resources; build Angular before packaging. Direct links to Angular routes are forwarded to the bundled `index.html`.

## Azure App Service deployment

The workflow `.github/workflows/main_contigo-backend.yml` builds and deploys an executable JAR to `contigo-backend`. It does not use container images or companion services.

Configure the App Service as **Linux / Java SE / Java 25** and leave its startup command empty. App Service detects the OneDeploy JAR (`app.jar`) and supplies the runtime port through `SERVER_PORT`.

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
