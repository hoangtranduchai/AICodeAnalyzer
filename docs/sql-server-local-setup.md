# SQL Server Local Setup

## 1. Create Database

Open SQL Server Management Studio and run:

```sql
CREATE DATABASE CodeAnalyzerDb;
GO
```

Then run the complete project SQL script:

```text
E:\CrawlCode\ai-code-analyzer-desktop\sql\ai-code-analyzer-complete.sql
```

## 2. Create Application Login

For local development, create a dedicated SQL login instead of using `sa`:

```sql
USE master;
GO

CREATE LOGIN code_analyzer_app
WITH PASSWORD = 'Change_This_Strong_Password_123!';
GO

USE CodeAnalyzerDb;
GO

CREATE USER code_analyzer_app FOR LOGIN code_analyzer_app;
CREATE ROLE code_analyzer_app_role;

GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.platforms TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.programming_handles TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.submissions TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.source_codes TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.ai_analysis_results TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.user_skill_scores TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.crawl_logs TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.app_settings TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.error_logs TO code_analyzer_app_role;

ALTER ROLE code_analyzer_app_role ADD MEMBER code_analyzer_app;
GO
```

## 3. Enable SQL Server TCP/IP

1. Open SQL Server Configuration Manager.
2. Go to `SQL Server Network Configuration`.
3. Select `Protocols for MSSQLSERVER` or your named instance.
4. Enable `TCP/IP`.
5. Open `TCP/IP` properties and set TCP Port to `1433` under `IPAll`.
6. Restart SQL Server service.

## 4. Configure The Java App

Copy the sample file:

```powershell
Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties
```

Set the local password as an environment variable:

```powershell
$env:DB_PASSWORD="Change_This_Strong_Password_123!"
```

Use this JDBC URL for local development:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=CodeAnalyzerDb;encrypt=true;trustServerCertificate=true
db.username=code_analyzer_app
db.password=${DB_PASSWORD}
```

## 5. Common Issues

- `Login failed`: check `db.username`, `DB_PASSWORD`, and SQL Server authentication mode.
- `Connection refused` or timeout: check SQL Server service, TCP/IP, firewall, and port `1433`.
- Certificate error: for local development, keep `encrypt=true;trustServerCertificate=true`.
- Database not found: run `sql/ai-code-analyzer-complete.sql`.
