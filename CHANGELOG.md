# Changelog

All notable project changes should be documented here.

This project follows the spirit of Keep a Changelog with sections for Added, Changed, Fixed, Security, and Documentation.

## 1.0.0-SNAPSHOT

### Added

- JavaFX desktop shell for AI Code Analyzer.
- Handle management for Codeforces and VJudge.
- SQL Server schema with platforms, handles, submissions, source code, analysis results, analysis jobs, skill scores, crawl logs, app settings, and error logs.
- Codeforces and VJudge crawler workflow.
- Authorized Chrome DevTools source acquisition.
- Gemini REST analyzer.
- OpenAI-compatible analyzer support.
- Rule-based analyzer and mock mode for offline demos.
- Skill scoring service.
- Scheduler for daily crawl workflows.
- PDF report export through OpenPDF.
- Excel report export through Apache POI.
- JUnit 5 test suite with H2-backed repository tests.
- Professional documentation set rebuilt from scratch.

### Security

- Secrets are configured through environment variables.
- Local `application.properties` is excluded from packaged resources.
- Crawler policy forbids captcha bypass and unauthorized source extraction.
- AI-risk wording is documented as non-final review signal.

### Documentation

- Added installation, user, test, security, release, architecture, database, configuration, operations, troubleshooting, AI analysis, crawler compliance, reporting, demo, roadmap, privacy, and project report documents.
