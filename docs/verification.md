# Verification

Validate the Angular build and Java tests on H2:

```powershell
cd frontend
npm ci
npm run build
cd ..
.\mvnw.cmd test
```

Before production, validate Flyway migrations and connectivity against a test Azure SQL database using the same networking, permissions, and App Service variables. Confirm that `flyway_schema_history` records V1 and V2 successfully.

Real Microsoft Graph email delivery requires external tenant credentials. Evidence storage is configured with `ATTACHMENTS_ROOT`; use `/home/data/evidence` in App Service.
