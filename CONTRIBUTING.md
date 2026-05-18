# Contributing

This project uses a small, disciplined workflow: understand the layer you are changing, keep edits focused, run the relevant tests, and document behavior that users or maintainers need to know.

## Development Setup

1. Install the tools listed in [INSTALLATION.md](INSTALLATION.md).
2. Create local `application.properties` from the example file.
3. Configure SQL Server and environment variables.
4. Run:

```powershell
mvn clean test
```

## Branch Naming

Use clear branch names:

```text
feature/report-export-polish
fix/vjudge-source-status
docs/rebuild-project-docs
test/repository-coverage
```

## Commit Style

Use concise, imperative commits:

```text
docs: rebuild architecture documentation
fix: handle quota-delayed analysis jobs
test: cover submission upsert deduplication
```

## Code Style

- Prefer existing package boundaries.
- Keep UI code in controllers and JavaFX resources.
- Keep business workflow in services.
- Keep SQL access in repositories.
- Keep provider-specific analysis logic in analyzer/service classes.
- Keep crawler-specific parsing inside crawler classes.
- Avoid broad refactors in feature branches.
- Do not introduce secrets or local machine paths into committed files.

## Test Expectations

Run all tests before handoff:

```powershell
mvn clean test
```

Add or update tests when changing:

- Repository behavior.
- Crawl result persistence.
- Analyzer mapping.
- Skill scoring.
- Report data.
- Validation or secret handling.
- Scheduler behavior.

## Documentation Expectations

Update documentation when changing:

- Setup steps.
- Configuration keys.
- Database schema.
- Crawler behavior.
- AI response schema.
- Report fields.
- Security boundaries.
- Demo flow.

## Pull Request Checklist

Before opening a PR:

- Code is focused on one concern.
- Tests pass.
- New behavior is documented.
- No generated secrets are staged.
- No unrelated formatting churn is included.
- Screenshots are included for visible UI changes.
- Report samples are checked when report generation changes.

## Review Priorities

Reviewers should focus on:

- Correctness.
- Data integrity.
- Security boundaries.
- User-visible behavior.
- Error handling.
- Test coverage.
- Documentation accuracy.
