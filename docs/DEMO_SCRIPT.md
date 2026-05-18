# Demo Script

This script provides a polished end-to-end demo path.

## Preparation

Before the demo:

1. Run `mvn clean test`.
2. Run SQL setup script.
3. Configure local `application.properties`.
4. Set `ai.mock-mode=true` if provider reliability is uncertain.
5. Prepare demo handles.
6. Prepare Chrome bot profile.
7. Prepare generated PDF and Excel examples as backup.

## Opening

Explain the project in one sentence:

```text
AI Code Analyzer Desktop crawls authorized competitive-programming submissions, analyzes source code, scores programming skills, and exports evaluation reports.
```

## Demo Flow

1. Show the repository structure.
2. Show `README.md` and documentation index.
3. Start the app.
4. Show dashboard.
5. Initialize Chrome bot.
6. Show DevTools endpoint health.
7. Add or select demo handles.
8. Run crawl for one handle.
9. Explain source availability states.
10. Open source detail.
11. Run analysis.
12. Explain algorithms, data structures, complexity, score, and AI-risk signal.
13. Show skill score.
14. Export PDF report.
15. Export Excel report.
16. Show security notes: no captcha bypass, no secret commits, no AI-risk final accusations.

## What To Say About AI Risk

Use this phrasing:

```text
The AI-risk field is a review signal. It highlights patterns that may deserve manual inspection, but it is not a final conclusion.
```

## Fallback Plan

If network fails:

- Use mock mode.
- Use seeded demo data.
- Show previously generated reports.

If SQL Server fails:

- Show schema documentation.
- Show automated tests.
- Use screenshots.

If Chrome bot fails:

- Show metadata crawl.
- Explain source unavailable state handling.

## Closing

Emphasize:

- Complete JavaFX desktop app.
- Real SQL Server schema.
- Ethical crawler boundaries.
- AI and rule-based analysis.
- Report generation.
- Automated tests.
- Professional documentation.
