# User Guide

This guide explains the daily workflow for AI Code Analyzer Desktop.

## First Launch

Start the application:

```powershell
mvn javafx:run
```

The application opens a JavaFX desktop workspace with dashboard, handle management, source review, reports, and settings.

If database configuration is invalid, fix `src/main/resources/application.properties` and restart the application.

## Recommended Workflow

1. Start the application.
2. Open or initialize the Chrome bot.
3. Sign in to Codeforces and VJudge inside the Chrome bot profile.
4. Add handles in the workspace.
5. Crawl one handle or all active handles.
6. Review source code and analysis results.
7. Export reports.
8. Enable scheduled daily crawl if needed.

## Chrome Bot

The Chrome bot is a normal Chrome session launched with a DevTools port. It lets the app read pages that the signed-in browser session is allowed to view.

Use it for:

- Viewing Codeforces source pages that require login.
- Viewing VJudge source pages that require login.
- Handling platform login and captcha manually.

Do not use it for:

- Bypassing captcha.
- Accessing private contests without permission.
- Reusing a personal browser profile with sensitive sessions.

Recommended steps:

1. Click `Initialize Browser Bot`.
2. Sign in to Codeforces or VJudge in the opened browser.
3. Confirm that the DevTools endpoint is reachable:

```text
http://localhost:9222/json/version
```

## Add Handles

In the handle workspace:

1. Choose platform: Codeforces or VJudge.
2. Enter the handle.
3. Add display name, group, notes, and consent status when available.
4. Keep the handle active if it should be included in full crawl runs.

The app stores each handle uniquely per platform.

## Crawl Submissions

Use per-handle crawl when validating one user.

Use full crawl when preparing a report or demo:

```text
Crawl & Analyze Now
```

The crawl stage:

- Fetches submission metadata.
- Retries source acquisition for submissions whose source is not crawled yet.
- Stores source code when available.
- Records unavailable states when source cannot be viewed.
- Writes crawl logs for audit and troubleshooting.

## Source Availability

Common source states:

| State | Meaning |
|---|---|
| `AVAILABLE` | Source was fetched and stored. |
| `LOGIN_REQUIRED` | The browser session is not signed in or lacks session access. |
| `PERMISSION_DENIED` | The current account cannot view the source. |
| `CAPTCHA_REQUIRED` | The platform requires manual challenge resolution. |
| `CONTEST_HIDDEN` | Source is hidden by contest or platform policy. |
| `SOURCE_NOT_AVAILABLE` | Source cannot be retrieved from the platform. |
| `OCR_REQUIRED` | VJudge source appears as an image and requires OCR. |
| `OCR_FAILED` | OCR could not produce reliable source text. |

Unavailable source is not a fatal error. The app still keeps metadata and explains why source was not stored.

## Analyze Source Code

Analysis can run automatically after crawl or manually from source detail screens.

The analyzer identifies:

- Programming language.
- Algorithms.
- Data structures.
- Time complexity.
- Space complexity.
- Code quality score.
- Algorithm and data-structure scores.
- AI-assistance risk signals.
- Warnings and confidence.

AI-risk output is a review signal only. It must not be treated as proof.

## Offline And Demo Modes

If no Gemini key is available, use one of these modes:

```properties
ai.mock-mode=true
```

or:

```properties
ai.provider=rule-based
```

Mock and rule-based modes are suitable for demos, tests, and offline development.

## Dashboard

Use the dashboard to review:

- Total handles.
- Submission volume.
- Latest crawl status.
- Analysis coverage.
- Skill score overview.
- Recent activity.

If dashboard numbers look empty, run the SQL seed script or crawl active handles.

## Reports

The Reports screen exports:

- PDF evaluation reports.
- Excel workbooks.

Generated files are stored under:

```text
reports/
```

Before exporting, make sure:

- At least one handle exists.
- Submissions are crawled.
- Source code exists or unavailable states are documented.
- Analysis results are available.
- Skill score calculation has completed.

## Scheduler

Use Settings to enable daily crawl.

Important settings:

```properties
scheduler.auto-crawl-enabled=false
scheduler.daily-run-time=01:00
```

The scheduler runs in the desktop process. If the application is closed, scheduled jobs do not run.

## Safe Usage Rules

- Crawl only public data or data the signed-in browser is authorized to view.
- Do not bypass captcha or anti-bot challenges.
- Do not store real API keys in source files.
- Do not use AI-risk as a final misconduct decision.
- Use demo accounts for classroom demonstrations.

## Quick Demo Flow

1. Launch the app.
2. Show dashboard.
3. Initialize Chrome bot.
4. Show Codeforces/VJudge login state.
5. Add or select demo handles.
6. Run crawl.
7. Open source detail.
8. Run analysis.
9. Show skill score.
10. Export PDF and Excel reports.
