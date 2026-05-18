# Configuration

Application configuration is loaded from Java properties and environment variables. Keep local secrets outside Git.

## Files

Template:

```text
src/main/resources/application.properties.example
```

Local file:

```text
src/main/resources/application.properties
```

The local file must not be committed.

## External Config

You can start the app with a specific config file:

```powershell
java -Dapp.config="E:\CrawlCode\AICodeAnalyzer\src\main\resources\application.properties" `
  -jar target/ai-code-analyzer-desktop-1.0.0-SNAPSHOT-all.jar
```

## Database Settings

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=CodeAnalyzerDb;encrypt=true;trustServerCertificate=true
db.username=code_analyzer_app
db.password=${DB_PASSWORD}
db.login-timeout-seconds=10
```

Use environment variables for passwords:

```powershell
$env:DB_PASSWORD="Change_This_Strong_Password_123!"
```

## Crawler Settings

```properties
crawler.default-delay-millis=1500
crawler.user-agent=AI-Code-Analyzer-Desktop/1.0
crawler.chrome-devtools-url=http://localhost:9222
```

`crawler.default-delay-millis` controls polite request spacing.

`crawler.chrome-devtools-url` points to the Chrome bot DevTools endpoint.

## Codeforces API Credentials

Optional:

```properties
codeforces.api-key=
codeforces.api-secret=
```

Prefer environment variables when credentials are introduced:

```text
CODEFORCES_API_KEY
CODEFORCES_API_SECRET
```

## Scheduler Settings

```properties
scheduler.auto-crawl-enabled=false
scheduler.daily-run-time=01:00
```

The scheduler runs inside the desktop application process. Closing the app stops scheduled execution.

## AI Settings

Gemini REST:

```properties
ai.provider=gemini-rest
ai.api-key-env=GEMINI_API_KEY
ai.api-key=
ai.model=gemini-2.5-flash
ai.endpoint=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
ai.timeout-seconds=45
ai.max-retries=3
ai.mock-mode=false
```

Rule-based:

```properties
ai.provider=rule-based
ai.mock-mode=false
```

Offline mock:

```properties
ai.mock-mode=true
```

## Language

The app supports Vietnamese and English resource bundles.

Run with explicit language:

```powershell
mvn javafx:run "-Dapp.language=vi"
mvn javafx:run "-Dapp.language=en"
```

## Recommended Profiles

### Development With Real AI

```properties
ai.provider=gemini-rest
ai.api-key-env=GEMINI_API_KEY
ai.mock-mode=false
scheduler.auto-crawl-enabled=false
```

### Offline Demo

```properties
ai.provider=gemini-rest
ai.mock-mode=true
scheduler.auto-crawl-enabled=false
```

### Deterministic Local Test

```properties
ai.provider=rule-based
ai.mock-mode=false
crawler.default-delay-millis=1500
```

## Secret Handling Rules

- Do not commit real values.
- Do not paste real keys into screenshots.
- Do not log environment variable values.
- Use placeholders in documentation.
- Rotate any secret that was accidentally exposed.
