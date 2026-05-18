# Project Report

## Topic

AI Code Analyzer Desktop: a JavaFX application for crawling authorized competitive-programming submissions, analyzing source code, evaluating programming skills, and exporting reports.

## Motivation

Programming courses often need a structured way to review student practice across online judges. Raw submission lists do not explain which algorithms a learner used, how code quality evolves, or where additional review is needed. This project combines crawling, source analysis, skill scoring, and reporting in one desktop application.

## Objectives

- Build a JavaFX desktop application.
- Store online judge data in SQL Server.
- Crawl Codeforces and VJudge submissions.
- Acquire source code only through authorized access.
- Analyze source code with AI and rule-based fallback.
- Score programming skills.
- Export PDF and Excel reports.
- Provide a safe and reproducible demo workflow.

## Scope

Included:

- Codeforces and VJudge handles.
- Submission metadata.
- Authorized source acquisition through Chrome DevTools.
- Gemini and OpenAI-compatible analysis paths.
- Rule-based fallback.
- Scheduler.
- PDF and Excel export.
- Automated tests.

Excluded:

- Captcha bypass.
- Unauthorized private source extraction.
- Final misconduct judgment.
- Cloud deployment.

## Architecture

The application uses layered architecture:

- JavaFX UI controllers.
- Application services.
- JDBC repositories.
- SQL Server database.
- Crawler adapters.
- Analyzer adapters.
- Report exporters.

See [ARCHITECTURE.md](ARCHITECTURE.md) for details.

## Database

The database contains:

- `platforms`
- `programming_handles`
- `submissions`
- `source_codes`
- `ai_analysis_results`
- `analysis_jobs`
- `user_skill_scores`
- `crawl_logs`
- `app_settings`
- `error_logs`

See [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) for details.

## Analysis

The analyzer identifies algorithms, data structures, complexity, code quality, and AI-assistance risk signals. AI-risk output is a review signal only.

See [AI_ANALYSIS_SPEC.md](AI_ANALYSIS_SPEC.md) for details.

## Testing

The test suite uses JUnit 5 and H2 for fast local validation. Manual testing covers UI, SQL Server, crawler, analysis, reports, and security behavior.

See [../TEST_PLAN.md](../TEST_PLAN.md) for details.

## Results

The project demonstrates:

- Complete desktop workflow.
- Real database persistence.
- Authorized crawler behavior.
- AI/rule-based source analysis.
- Skill scoring.
- Exportable reports.
- Professional documentation.

## Limitations

- Live crawling depends on platform availability and page format.
- Source access depends on browser session permissions.
- AI analysis depends on provider quota unless mock/rule-based mode is used.
- The scheduler runs only while the desktop app is open.

## Future Work

Planned improvements include installer packaging, encrypted source storage, configurable rubrics, UI automation tests, additional online judge platforms, and richer analytics.

## Conclusion

AI Code Analyzer Desktop is a complete Java desktop project with practical architecture, persistence, crawling, analysis, reporting, tests, and documentation. It is suitable for academic demonstration and further product-quality development.
