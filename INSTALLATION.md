# Installation Guide

This guide sets up AI Code Analyzer Desktop on Windows for local development, testing, and demonstration.

## Requirements

- Windows 10 or Windows 11.
- JDK 21.
- Maven 3.9 or newer.
- Microsoft SQL Server 2019, 2022, or SQL Server Express.
- SQL Server Management Studio.
- Google Chrome.
- Internet access for Maven dependencies and online judge crawling.
- Gemini API key if real AI analysis is required.

Verify the core tools:

```powershell
java -version
javac -version
mvn -version
```

## Clone Or Open The Project

The expected project root is:

```text
E:\CrawlCode\AICodeAnalyzer
```

From the project root, verify that Maven can see the project:

```powershell
mvn -q -DskipTests validate
```

## Install SQL Server

Install SQL Server with one of these modes:

- Default instance: `localhost:1433`.
- Named Express instance: `localhost\SQLEXPRESS`.

Open SQL Server Configuration Manager and ensure:

- SQL Server service is running.
- TCP/IP is enabled.
- Port `1433` is enabled if you use the default TCP example.
- SQL Server Browser is running if you rely on named instance discovery.

## Create The Database

Open SQL Server Management Studio and run:

```text
sql/ai-code-analyzer-complete.sql
```

The script creates:

- Database `CodeAnalyzerDb`.
- Core tables, constraints, foreign keys, indexes, app settings, and demo data.
- Default platforms for Codeforces and VJudge.

Quick validation:

```sql
USE CodeAnalyzerDb;

SELECT COUNT(*) AS platforms FROM dbo.platforms;
SELECT COUNT(*) AS handles FROM dbo.programming_handles;
SELECT COUNT(*) AS submissions FROM dbo.submissions;
SELECT COUNT(*) AS source_codes FROM dbo.source_codes;
```

## Create Application Configuration

Copy the example file:

```powershell
Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties
```

Use the default TCP configuration:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=CodeAnalyzerDb;encrypt=true;trustServerCertificate=true
db.username=code_analyzer_app
db.password=${DB_PASSWORD}
```

Or use SQL Server Express:

```properties
db.url=jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=CodeAnalyzerDb;encrypt=true;trustServerCertificate=true
db.username=code_analyzer_app
db.password=${DB_PASSWORD}
```

Never commit `application.properties`.

## Configure Secrets

For the current PowerShell session:

```powershell
$env:DB_PASSWORD="Change_This_Strong_Password_123!"
$env:GEMINI_API_KEY="your_gemini_api_key"
```

For persistent Windows user variables:

```powershell
setx DB_PASSWORD "Change_This_Strong_Password_123!"
setx GEMINI_API_KEY "your_gemini_api_key"
```

Open a new terminal after `setx`.

## Configure AI Mode

Real Gemini analysis:

```properties
ai.provider=gemini-rest
ai.api-key-env=GEMINI_API_KEY
ai.model=gemini-2.5-flash
ai.mock-mode=false
```

Offline demo mode:

```properties
ai.provider=gemini-rest
ai.mock-mode=true
```

Rule-based only:

```properties
ai.provider=rule-based
ai.mock-mode=false
```

## Configure Chrome Bot

The app can launch Chrome for you from the UI. You can also start it manually:

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" `
  --remote-debugging-port=9222 `
  --user-data-dir="$env:TEMP\ai-code-analyzer-chrome-profile"
```

Then open:

```text
http://localhost:9222/json/version
```

If a JSON response appears, the DevTools endpoint is ready.

## Build And Test

Run all tests:

```powershell
mvn clean test
```

Build the shaded JAR:

```powershell
mvn clean package
```

Run the JAR:

```powershell
java -jar target/ai-code-analyzer-desktop-1.0.0-SNAPSHOT-all.jar
```

Run with an external configuration file:

```powershell
java -Dapp.config="E:\CrawlCode\AICodeAnalyzer\src\main\resources\application.properties" `
  -jar target/ai-code-analyzer-desktop-1.0.0-SNAPSHOT-all.jar
```

## Run In Development

```powershell
mvn javafx:run
```

Force Vietnamese:

```powershell
mvn javafx:run "-Dapp.language=vi"
```

Force English:

```powershell
mvn javafx:run "-Dapp.language=en"
```

## Final Setup Checklist

- JDK 21 works.
- Maven works.
- SQL Server is running.
- `CodeAnalyzerDb` exists.
- `application.properties` exists locally.
- `DB_PASSWORD` is set.
- `GEMINI_API_KEY` is set or mock/rule-based mode is enabled.
- Chrome bot endpoint responds at `http://localhost:9222/json/version`.
- `mvn clean test` passes.
- `mvn javafx:run` starts the desktop application.
