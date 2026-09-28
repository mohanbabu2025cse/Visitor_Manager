$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$settingsPath = Join-Path $PSScriptRoot 'local-config/mysql.json'
if (-not (Test-Path -LiteralPath $settingsPath)) {
    throw 'Missing local-config/mysql.json. See the MySQL section of README.md.'
}
$databaseSettings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
$env:DB_USERNAME = $databaseSettings.username
$env:DB_PASSWORD = $databaseSettings.password
$env:DB_URL = 'jdbc:mysql://localhost:3306/visitor_management?createDatabaseIfNotExist=true'
try {
    & java -jar target/visitor-management-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
}
