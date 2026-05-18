# Operations Runbook

This runbook is for running the application reliably during development, demo, or review.

## Standard Startup

1. Start SQL Server.
2. Open PowerShell at the project root.
3. Confirm environment variables:

```powershell
$env:DB_PASSWORD
$env:GEMINI_API_KEY
```

4. Start the app:

```powershell
mvn javafx:run
```

5. Initialize Chrome bot from the UI or manually.

## Health Checks

Database:

```sql
USE CodeAnalyzerDb;
SELECT COUNT(*) FROM dbo.platforms;
SELECT TOP 5 * FROM dbo.crawl_logs ORDER BY started_at DESC;
```

Chrome bot:

```text
http://localhost:9222/json/version
```

Build:

```powershell
mvn clean test
```

## Manual Crawl Procedure

1. Open the app.
2. Initialize Chrome bot.
3. Sign in to the target online judge if source access requires login.
4. Add or select active handles.
5. Run per-handle crawl first for validation.
6. Run full crawl after validation.
7. Review crawl log summary.
8. Open source detail for at least one new submission.

## Analysis Procedure

1. Confirm AI mode in `application.properties`.
2. If using real provider, confirm API key is set.
3. Run analysis on a known source.
4. Check analysis result fields.
5. Confirm skill scores refresh.
6. If rate-limited, wait or switch to mock/rule-based mode for demo.

## Report Procedure

1. Confirm handles have submissions.
2. Confirm source and analysis data exists.
3. Open Reports.
4. Generate PDF.
5. Generate Excel.
6. Open generated files from `reports/`.
7. Review report for sensitive data before sharing.

## Scheduled Crawl Procedure

1. Configure daily run time.
2. Enable auto crawl.
3. Keep the desktop app running.
4. Confirm next execution in logs or settings.
5. After execution, inspect `crawl_logs`.

## Backup Procedure

For demo safety:

1. Back up `CodeAnalyzerDb`.
2. Keep a copy of generated reports.
3. Save screenshots of the successful demo path.
4. Keep mock mode available as fallback.

## Recovery Procedure

If the app fails to start:

1. Run `mvn clean test`.
2. Check `application.properties`.
3. Check SQL Server service.
4. Check DB login and password.
5. Temporarily set `ai.mock-mode=true`.
6. Restart Chrome bot.

If crawl fails:

1. Check browser login.
2. Check DevTools endpoint.
3. Check platform availability.
4. Check captcha or permission state.
5. Reduce crawl volume.

If reports fail:

1. Confirm `reports/` exists.
2. Confirm no generated report is open and locked by another program.
3. Confirm analysis data exists.
4. Run tests for report services.

## Demo Safe Mode

Use this when reliability matters more than live network behavior:

```properties
ai.mock-mode=true
scheduler.auto-crawl-enabled=false
```

Prepare demo data from SQL seed script and avoid relying on live provider quota.
