# Verification

Validate the project with Java tests on H2, domain tests, and JavaScript UI tests:

```powershell
.\mvnw.cmd test
node --test scripts/frontend-tests.mjs
```

Before production, validate Flyway migrations and connectivity against a test Azure SQL database using the same networking, permissions, and App Service variables. Confirm that `flyway_schema_history` records V1 and V2 successfully.

Real Microsoft Graph email delivery requires external tenant credentials. Evidence storage is configured with `ATTACHMENTS_ROOT`; use `/home/data/evidence` in App Service.
