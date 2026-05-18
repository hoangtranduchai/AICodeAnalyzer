# Architecture

AI Code Analyzer Desktop follows a layered desktop architecture. The design keeps JavaFX UI concerns separate from workflow orchestration, persistence, crawling, analysis, reporting, and configuration.

## System Context

```text
User
  |
  v
JavaFX Desktop UI
  |
  v
Application Services
  |
  +--> SQL Server
  +--> Codeforces API and pages
  +--> VJudge status and solution pages
  +--> Chrome DevTools browser session
  +--> Gemini or OpenAI-compatible API
  +--> PDF and Excel files
```

## Package Responsibilities

| Package | Responsibility |
|---|---|
| `app` | Main JavaFX launch, application context, dependency wiring. |
| `config` | Load database, AI, scheduler, and resource configuration. |
| `model` | Domain data objects used across services and repositories. |
| `repository` | JDBC persistence and SQL Server data access. |
| `service` | Application workflows and business orchestration. |
| `crawler` | Platform-specific submission and source acquisition. |
| `analyzer` | Prompt building, AI analyzer contracts, rule-based analyzer. |
| `scheduler` | Daily workflow scheduling. |
| `ui.controller` | JavaFX screen controllers. |
| `ui.logging` | UI-facing log bus and Logback appender. |
| `report` | Report data builders and PDF/Excel exporters. |
| `util` | Validation and secret masking helpers. |
| `exception` | Application-specific exception types. |

## Composition Root

`ApplicationContext` constructs the main repositories and services:

- `CrawlService`
- `AnalysisService`
- `BackendWorkflowService`
- `SchedulerManager`
- `DashboardService`
- `HandleAccountService`
- `SourceCodeDetailService`
- `ReportService`
- `ExcelReportService`

This keeps object creation centralized and avoids spreading dependency construction across controllers.

## Main Workflow

```text
Handle selected or scheduled job starts
  |
  v
CrawlService loads active handles
  |
  v
Platform crawler fetches submission metadata
  |
  v
SourceFetcher attempts authorized source acquisition through Chrome CDP
  |
  v
SubmissionUpsertService saves new or updated submissions
  |
  v
BackendWorkflowService finds pending source code
  |
  v
AnalysisService runs configured analyzer
  |
  v
AiAnalysisResultRepository saves analysis
  |
  v
SkillScoringService refreshes handle scores
  |
  v
Dashboard and Reports read persisted data
```

## Crawler Design

The crawler layer has two responsibilities:

- Normalize submission metadata from each online judge.
- Acquire source code only when the current browser session can view it.

Important classes:

- `OnlineJudgeCrawler`
- `CodeforcesCrawler`
- `VJudgeCrawler`
- `SourceFetcher`
- `PlaywrightCdpSourceFetcher`
- `GeminiImageOcrClient`
- `CrawlerRateLimiter`

Crawler output is represented as `CrawlResult` containing normalized `CrawledSubmission` records.

## Analysis Design

`AnalysisService` selects analyzer behavior through `AiConfig`.

Supported modes:

- `gemini-rest`: Gemini REST analyzer.
- OpenAI-compatible endpoint: OpenAI analyzer.
- `rule-based`: deterministic local analyzer.
- `mock-mode`: local deterministic demo behavior.

Rate limits are handled by marking analysis jobs as quota-delayed instead of continuously retrying.

## Persistence Design

Repositories use JDBC directly. The schema is intentionally explicit and simple:

- One platform has many handles.
- One handle has many submissions.
- One submission has at most one source code record.
- One submission has many analysis results.
- One source code record has one analysis job.
- One handle has many score snapshots.

Submission deduplication is enforced by:

```text
(platform_id, platform_submission_id)
```

## Scheduler Design

`SchedulerManager` uses `ScheduledExecutorService`.

The scheduler runs only while the desktop process is alive. It is not a Windows service.

Scheduler settings are loaded from:

- `application.properties`
- `dbo.app_settings`

## UI Design

JavaFX controllers should remain thin:

- Read user input.
- Call services.
- Render service output.
- Display errors safely.

Business logic belongs in services, not controllers.

## Reporting Design

Report generation is split into:

- `ReportDataBuilder`: collect and shape report data.
- `PdfReportExporter`: write PDF.
- `ExcelReportService` and related data classes: write Excel.

Generated files go to:

```text
reports/
```

## Architectural Constraints

- No crawler should directly write SQL.
- No controller should directly parse online judge HTML.
- No repository should call AI or crawler APIs.
- No analyzer should mutate UI state.
- No report exporter should crawl or analyze source code.
- No code should log secrets.
