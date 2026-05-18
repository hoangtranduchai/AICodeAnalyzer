# Test Plan

This test plan defines how to verify AI Code Analyzer Desktop before development handoff, classroom demonstration, or release.

## Goals

- Confirm that the application starts reliably.
- Confirm that configuration errors are visible and recoverable.
- Confirm that SQL Server persistence works.
- Confirm that crawler workflows respect access boundaries.
- Confirm that analyzers produce valid persisted results.
- Confirm that reports are generated correctly.
- Confirm that security-sensitive values are not leaked.

## Test Environments

| Environment | Purpose |
|---|---|
| Local Windows development | Primary functional validation. |
| H2 test database | Fast repository and service tests. |
| SQL Server local database | Real schema and integration verification. |
| Offline mock mode | Demo and no-network fallback. |
| Real AI provider mode | Gemini/OpenAI-compatible behavior. |

## Automated Test Suite

Run:

```powershell
mvn clean test
```

Expected result:

- Build succeeds.
- JUnit 5 test suite passes.
- No secret values appear in console output.

Areas covered:

- Configuration loading.
- Database connection helpers.
- Repository behavior.
- Submission upsert behavior.
- Crawler request and parsing logic.
- Rule-based and AI service coordination.
- Skill scoring and feedback.
- Report data building.
- Scheduler behavior.
- Validation and secret masking utilities.

## Manual Smoke Test

1. Start SQL Server.
2. Run the schema script.
3. Configure `application.properties`.
4. Start the app with `mvn javafx:run`.
5. Confirm the main window opens.
6. Confirm dashboard loads without crashing.
7. Add a test handle.
8. Start Chrome bot.
9. Sign in if needed.
10. Crawl one handle.
11. Open source detail.
12. Run analysis.
13. Export PDF and Excel.
14. Close and reopen the app.
15. Confirm saved data is still visible.

## Database Tests

Verify:

- `CodeAnalyzerDb` exists.
- Required tables exist.
- Foreign keys are present.
- Unique key on `(platform_id, platform_submission_id)` prevents duplicate submissions.
- `source_codes` is one-to-one with `submissions`.
- Cascading deletes behave as expected for handle-owned data.
- Indexes exist for dashboard, crawl queue, analysis jobs, and report queries.

Useful SQL:

```sql
SELECT name FROM sys.tables ORDER BY name;
SELECT name FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.submissions');
SELECT COUNT(*) FROM dbo.crawl_logs;
```

## Crawler Tests

Codeforces:

- Crawl public metadata without login.
- Crawl source through signed-in Chrome when source is visible.
- Save unavailable state when source cannot be viewed.
- Handle rate limit or network errors gracefully.

VJudge:

- Crawl status data.
- Fetch source through signed-in Chrome when allowed.
- Save `LOGIN_REQUIRED`, `PERMISSION_DENIED`, or `CAPTCHA_REQUIRED` when appropriate.
- Use OCR only when an authorized image snapshot is visible and AI key is available.

Compliance expectations:

- No captcha bypass.
- No hidden contest bypass.
- No cookie or token logging.

## Analyzer Tests

Rule-based mode:

- Works without API keys.
- Produces deterministic output.
- Generates algorithms, data structures, score, and summary fields.

AI mode:

- Uses configured provider and endpoint.
- Parses valid JSON responses.
- Handles 429 and 5xx retry behavior.
- Marks quota-delayed jobs without repeatedly hammering the provider.

Mock mode:

- Works offline.
- Produces stable demo output.

## Report Tests

PDF:

- File is created in `reports/`.
- Report includes handle identity, summary, scores, and analysis evidence.
- Long text does not crash export.

Excel:

- Workbook opens in Excel.
- Columns are readable.
- Numeric score columns are numeric.
- Multiple handles can be exported.

## Security Tests

Verify:

- `application.properties` is ignored by Git.
- `.env` is ignored by Git.
- Secrets are loaded from environment variables.
- Logs mask `password`, `token`, and `api-key` style values.
- Source availability errors do not include cookies.
- AI-risk wording remains non-accusatory.

## Acceptance Criteria

The project is acceptable for handoff when:

- `mvn clean test` passes.
- SQL Server setup works from a clean database.
- App starts with documented configuration.
- At least one handle can be crawled.
- Source detail and analysis screens work.
- PDF and Excel exports are generated.
- Security checklist passes.
- Demo script can be completed end to end.
