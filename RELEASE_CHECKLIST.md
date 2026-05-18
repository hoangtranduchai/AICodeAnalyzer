# Release Checklist

Use this checklist before a classroom demo, project handoff, tagged release, or final submission.

## Version

- Project version in `pom.xml` is correct.
- `CHANGELOG.md` has an entry for the release.
- Documentation reflects current behavior.
- Generated artifacts from old builds are not confused with the new release.

## Build

Run:

```powershell
mvn clean test
mvn clean package
```

Confirm:

- Tests pass.
- Shaded JAR is created under `target/`.
- Application starts from the JAR.

## Database

Confirm:

- `sql/ai-code-analyzer-complete.sql` is the canonical SQL file.
- A clean SQL Server database can be created from the script.
- Demo data exists if the release/demo requires it.
- Indexes and constraints are present.

## Configuration

Confirm:

- `application.properties.example` is current.
- Local `application.properties` is not committed.
- `.env` is not committed.
- `DB_PASSWORD` is configured.
- AI mode is intentionally selected: real provider, mock, or rule-based.

## Crawler

Confirm:

- Chrome bot starts.
- DevTools endpoint responds.
- Codeforces metadata crawl works.
- VJudge status crawl works.
- Source unavailable states are displayed clearly.
- Captcha and permission boundaries are respected.

## AI Analysis

Confirm:

- Rule-based analyzer works offline.
- Mock mode works for demo fallback.
- Real AI provider works if enabled.
- Rate limit handling is acceptable.
- AI-risk output is not accusatory.

## Reports

Confirm:

- PDF export works.
- Excel export works.
- Reports open successfully.
- Output is stored under `reports/`.
- Report content matches the selected handles and analysis data.

## UI

Confirm:

- Main shell opens.
- Dashboard loads.
- Handle management works.
- Source detail screen works.
- Settings screen works.
- Language resources load.
- Long errors are readable and not catastrophic.

## Security

Confirm:

- No real secrets in Git.
- No cookies or tokens in logs.
- Browser bot uses an isolated profile.
- Reports are reviewed before sharing.
- Security notes are included in handoff.

## Documentation

Confirm these files exist and are current:

- `README.md`
- `INSTALLATION.md`
- `USER_GUIDE.md`
- `TEST_PLAN.md`
- `SECURITY_NOTES.md`
- `CONTRIBUTING.md`
- `CHANGELOG.md`
- `docs/ARCHITECTURE.md`
- `docs/DATABASE_SCHEMA.md`
- `docs/CONFIGURATION.md`
- `docs/OPERATIONS_RUNBOOK.md`
- `docs/TROUBLESHOOTING.md`
- `docs/AI_ANALYSIS_SPEC.md`
- `docs/CRAWLER_COMPLIANCE.md`
- `docs/REPORTING_SPEC.md`
- `docs/DEMO_SCRIPT.md`
- `docs/PROJECT_REPORT.md`

## Demo Assets

Prepare:

- Screenshot of successful build/test.
- Screenshot of SQL Server database.
- Screenshot of dashboard.
- Screenshot of Chrome bot.
- Screenshot of source detail.
- Screenshot of analysis result.
- Generated PDF report.
- Generated Excel report.

## Final Handoff

The release is ready when:

- Build passes.
- App launches.
- Database setup is reproducible.
- Demo flow works.
- Documentation is coherent.
- Security checklist passes.
