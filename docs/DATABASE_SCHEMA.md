# Database Schema

The canonical database file is:

```text
sql/ai-code-analyzer-complete.sql
```

The target database is:

```text
CodeAnalyzerDb
```

## Entity Overview

```text
platforms
  |
  v
programming_handles
  |
  v
submissions
  |
  +--> source_codes
  |       |
  |       v
  |   analysis_jobs
  |
  +--> ai_analysis_results

programming_handles
  |
  v
user_skill_scores

crawl_logs
app_settings
error_logs
```

## `platforms`

Stores supported online judge platforms.

Key columns:

- `platform_id`
- `code`
- `name`
- `base_url`
- `api_url`
- `is_active`

Constraints:

- Primary key: `platform_id`
- Unique: `code`

Expected codes:

- `CODEFORCES`
- `VJUDGE`

## `programming_handles`

Stores tracked handles.

Key columns:

- `handle_id`
- `platform_id`
- `handle`
- `display_name`
- `group_name`
- `rating`
- `rank_name`
- `general_evaluation`
- `consent_status`
- `is_active`
- `last_crawled_at`
- `notes`

Constraints:

- Primary key: `handle_id`
- Foreign key: `platform_id`
- Unique: `(platform_id, handle)`
- Rating must be non-negative when present.

## `submissions`

Stores normalized submission metadata.

Key columns:

- `submission_id`
- `platform_id`
- `handle_id`
- `platform_submission_id`
- `problem_code`
- `problem_name`
- `contest_id`
- `language`
- `verdict`
- `submitted_at`
- `execution_time_ms`
- `memory_bytes`
- `problem_rating`
- `problem_tags`
- `source_url`
- `source_crawl_status`
- `source_crawled_at`
- `source_crawl_error`

Constraints:

- Primary key: `submission_id`
- Foreign keys: `platform_id`, `handle_id`
- Unique: `(platform_id, platform_submission_id)`
- `handle_id` cascades on delete.

Allowed source crawl statuses:

- `PENDING`
- `CRAWLED`
- `FAILED`
- `SKIPPED`

## `source_codes`

Stores source code fetched for submissions.

Key columns:

- `source_code_id`
- `submission_id`
- `code_content`
- `code_hash`
- `line_count`
- `char_count`
- `fetched_at`
- `storage_type`
- `is_encrypted`

Constraints:

- Primary key: `source_code_id`
- Foreign key: `submission_id`
- Unique: `submission_id`

Current storage type:

- `DATABASE`

## `ai_analysis_results`

Stores analyzer output for a submission.

Key columns:

- `analysis_id`
- `submission_id`
- `analyzer_type`
- `analyzer_version`
- `model_name`
- `data_structures`
- `algorithms`
- `complexity_estimate`
- `code_quality_score`
- `ai_risk_score`
- `ai_risk_level`
- `summary`
- `raw_response`
- `prompt_hash`

Constraints:

- Primary key: `analysis_id`
- Foreign key: `submission_id`
- Scores must be between 0 and 100 when present.

## `analysis_jobs`

Tracks analysis queue state for each source code record.

Key columns:

- `analysis_job_id`
- `source_code_id`
- `submission_id`
- `status`
- `attempt_count`
- `next_retry_at`
- `started_at`
- `finished_at`
- `last_analysis_id`
- `last_error`

Allowed statuses:

- `PENDING`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `QUOTA_DELAYED`
- `SKIPPED`

## `user_skill_scores`

Stores aggregate skill scores by handle and period.

Key columns:

- `score_id`
- `handle_id`
- `period_start`
- `period_end`
- `data_structure_score`
- `algorithm_score`
- `problem_solving_score`
- `code_quality_score`
- `practice_consistency_score`
- `ai_usage_risk_score`
- `overall_score`
- `summary`
- `generated_at`

Constraints:

- Primary key: `score_id`
- Foreign key: `handle_id`
- Unique: `(handle_id, period_start, period_end)`
- Scores must be between 0 and 100 when present.

## `crawl_logs`

Stores crawl execution summaries.

Key columns:

- `crawl_log_id`
- `job_type`
- `status`
- `started_at`
- `finished_at`
- `total_handles`
- `total_new_submissions`
- `total_errors`
- `message`

Common job types:

- `MANUAL`
- `DIRECT`
- `SCHEDULED`

## `app_settings`

Stores runtime settings that can be changed from the app.

Important keys:

- `scheduler.auto-crawl-enabled`
- `scheduler.daily-run-time`

## `error_logs`

Stores sanitized application errors.

Key columns:

- `error_log_id`
- `component`
- `severity`
- `sanitized_message`
- `stack_trace`
- `created_at`

Do not store secrets in this table.

## Important Indexes

Important query paths:

- Handles by platform.
- Submissions by handle.
- Recent submissions by submitted time.
- Source crawl queue by status.
- Analysis jobs by status and retry time.
- Analysis results by submission and created time.
- Skill scores by handle and period.
- Crawl logs by start time.

## Migration Rule

For this project, keep one canonical SQL file unless a formal migration tool is introduced. If schema changes are required, update `sql/ai-code-analyzer-complete.sql` and document the change in `CHANGELOG.md`.
