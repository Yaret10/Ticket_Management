param([switch]$DatabaseOnly)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
java scripts/GenerateLocalConfig.java
if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar la configuración.' }
Get-Content -LiteralPath '.env' | ForEach-Object {
  if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
docker compose up -d db db-init
if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar SQL Server. Revise Docker Desktop.' }
if (-not $DatabaseOnly) { & .\mvnw.cmd spring-boot:run }
