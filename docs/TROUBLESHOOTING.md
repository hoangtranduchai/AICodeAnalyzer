# Troubleshooting

Use this guide when setup, crawling, analysis, or reporting fails.

## App Does Not Start

Symptoms:

- JavaFX window never opens.
- Maven exits with build errors.

Actions:

```powershell
java -version
mvn -version
mvn clean test
```

Check:

- JDK is version 21.
- Maven is available.
- Dependencies can be downloaded.
- `application.properties` is valid.

## Cannot Connect To SQL Server

Symptoms:

- Login timeout.
- Connection refused.
- Login failed.

Actions:

- Start SQL Server service.
- Enable TCP/IP.
- Confirm port `1433` or named instance.
- Confirm database `CodeAnalyzerDb` exists.
- Confirm username and password.

Test in SQL Server Management Studio first.

Common JDBC URLs:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=CodeAnalyzerDb;encrypt=true;trustServerCertificate=true
db.url=jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=CodeAnalyzerDb;encrypt=true;trustServerCertificate=true
```

## Password Placeholder Is Not Resolved

Symptoms:

- App tries to use `${DB_PASSWORD}` literally.

Actions:

```powershell
$env:DB_PASSWORD="Change_This_Strong_Password_123!"
```

If using `setx`, open a new terminal after setting the variable.

## Chrome Bot Is Not Ready

Symptoms:

- Source crawl says login required.
- DevTools endpoint cannot be reached.

Actions:

1. Close old bot sessions.
2. Start Chrome with remote debugging:

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" `
  --remote-debugging-port=9222 `
  --user-data-dir="$env:TEMP\ai-code-analyzer-chrome-profile"
```

3. Open:

```text
http://localhost:9222/json/version
```

## Codeforces Source Is Missing

Possible causes:

- The browser is not signed in.
- The current account cannot view that source.
- The contest hides source.
- The page format changed.
- Rate limiting occurred.

Expected behavior:

- Metadata may still be saved.
- Source state should explain the failure.

## VJudge Source Is Missing

Possible causes:

- Login required.
- Share code is disabled.
- Contest source is hidden.
- Captcha is required.
- Source appears as image and OCR is unavailable.

Actions:

- Sign in manually.
- Resolve captcha manually.
- Confirm you can view the solution page in the bot browser.
- Configure `GEMINI_API_KEY` if OCR is needed.

## AI Analysis Fails

Symptoms:

- 401, 403, 429, 5xx, timeout.

Actions:

- Confirm `GEMINI_API_KEY`.
- Confirm provider and endpoint.
- Reduce batch size.
- Wait after quota errors.
- Use mock mode for demo:

```properties
ai.mock-mode=true
```

- Use rule-based mode:

```properties
ai.provider=rule-based
```

## Reports Do Not Export

Possible causes:

- `reports/` is missing.
- Output file is open in Excel or PDF viewer.
- No data exists for selected report.
- File permission issue.

Actions:

- Close existing report files.
- Recreate `reports/`.
- Run crawl and analysis first.
- Check logs.

## Tests Fail

Actions:

```powershell
mvn clean test -e
```

Check:

- JDK version.
- Local changes.
- Test resources.
- H2 compatibility behavior.

## Git Shows Secret Files

Actions:

```powershell
git status --short
```

Do not stage:

- `application.properties`
- `.env`
- generated browser profiles
- reports with private data unless intentionally included

If a secret was committed, rotate it immediately.
