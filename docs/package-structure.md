# Java Package Structure

Base package: `com.example.aicodeanalyzer`

## `app`

- `MainApp`: JavaFX entry point and primary stage initialization.
- `ApplicationContext`: Composition root for sharing services, repositories, scheduler, and UI dependencies.

## `config`

- `DatabaseConfig`: SQL Server connection settings and JDBC resource creation.
- `DatabaseConnectionFactory`: Creates JDBC connections and validates SQL Server connectivity.
- `ConnectionTestResult`: User-facing result object for database connection checks.
- `AiConfig`: AI provider, endpoint, model, timeout, and API key reference.
- `SchedulerConfig`: ScheduledExecutorService daily runtime settings.

## `model`

- `Platform`: Online judge platform such as Codeforces or VJudge.
- `HandleAccount`: Tracked programming handle mapped to `programming_handles`.
- `Submission`: Submission metadata.
- `SourceCode`: Source code content or storage metadata.
- `AiAnalysisResult`: AI/rule-based analysis output.
- `SkillScore`: Aggregated skill score per handle and period.
- `CrawlLog`: One crawl execution summary.

## `repository`

- `PlatformRepository`: Database operations for platforms.
- `JdbcRepositorySupport`: Shared JDBC helpers for connection access, generated keys, and date/time mapping.
- `HandleAccountRepository`: Database operations for handles.
- `SubmissionRepository`: Database operations for submissions.
- `SourceCodeRepository`: Database operations for source code records.
- `AiAnalysisResultRepository`: Database operations for analysis results.
- `SkillScoreRepository`: Database operations for skill scores.
- `CrawlLogRepository`: Database operations for crawl logs and crawl items.

## `service`

- `HandleAccountService`: Handle validation and management.
- `SubmissionUpsertService`: Duplicate-safe persistence for crawled/imported submissions and source code.
- `CrawlService`: Coordinates crawler adapters and crawl persistence.
- `AnalysisService`: Coordinates source code analyzers and stores results.
- `SkillScoringService`: Aggregates analysis into user skill scores.
- `DashboardService`: Builds dashboard metrics and chart datasets.
- `ReportService`: Builds and exports reports.
- `ExcelReportService`: Builds and exports Excel workbooks.

## `crawler`

- `OnlineJudgeCrawler`: Common crawler contract.
- `CodeforcesCrawler`: Codeforces metadata crawler with signed `includeSources` support and authorized source fallback.
- `VJudgeCrawler`: VJudge status crawler with authorized source page/snapshot fallback.
- `SourceFetcher`: Common source acquisition contract.
- `PlaywrightCdpSourceFetcher`: Reads source pages through the user's authorized Chrome CDP bot profile.
- `GeminiImageOcrClient`: OCR fallback for authorized VJudge source snapshots.
- `SourceFetchResult`: Source fetch status, origin, code text, and reason.
- `SourceAvailability`: Detailed source states such as `AVAILABLE`, `LOGIN_REQUIRED`, `CAPTCHA_REQUIRED`, and `OCR_FAILED`.
- `SourceOrigin`: Source provenance such as `CODEFORCES_API`, `CODEFORCES_AUTHORIZED_HTML`, or `VJUDGE_AUTHORIZED_SNAPSHOT_OCR`.
- `CrawlerRateLimiter`: Request delay, retry, and backoff helper.
- `CrawlResult`: Normalized crawler output.

## `analyzer`

- `CodeAnalyzer`: Common analyzer contract.
- `RuleBasedCodeAnalyzer`: Deterministic source code analyzer.
- `OpenAiCodeAnalyzer`: AI REST analyzer.
- `AnalysisPromptBuilder`: Safe prompt/payload builder.

## `scheduler`

- `SchedulerManager`: Starts, stops, and configures the daily ScheduledExecutorService workflow.
- `SchedulerSettingsService`: Persists daily crawl settings and reads the latest crawl log.

## `ui`

Shared JavaFX shell code currently lives in `MainApp`; feature-specific screens are under `ui.controller`.

## `ui.controller`

- `DashboardController`: Workspace overview metrics and charts.
- `HandleController`: Quick add, edit/delete, and per-handle crawl controls.
- `SourceCodeDetailController`: Submission source detail and analysis action.
- `SchedulerSettingsController`: Scheduler and AI settings screen.

## `report`

- `ReportDataBuilder`: Builds report datasets.
- `ReportExporter`: Common report exporter contract.
- `PdfReportExporter`: PDF export using OpenPDF.
- `ExcelReportData`: Excel report dataset.
- `EvaluationReportData`: PDF evaluation report dataset.

## `util`

- `ValidationUtils`: Input validation helpers.
- `SecretUtils`: Secret loading and masking helpers.

## `exception`

- `AppException`: Base application exception.
- `DatabaseException`: SQL Server errors.
- `CrawlerException`: Crawler/network/parsing/rate-limit errors.
- `AnalyzerException`: Rule-based or AI analyzer errors.
- `ReportException`: PDF/Excel/report generation errors.
