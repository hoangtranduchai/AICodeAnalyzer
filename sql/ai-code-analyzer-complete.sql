-- Canonical SQL Server schema for AI Code Analyzer Desktop.
-- Keep this as the only .sql file in the project.
-- Logical mapping:
--   Users           -> dbo.programming_handles
--   Submissions     -> dbo.submissions + dbo.source_codes
--   AnalysisResults -> dbo.ai_analysis_results

IF DB_ID(N'CodeAnalyzerDb') IS NULL
BEGIN
    CREATE DATABASE CodeAnalyzerDb;
END
GO

USE CodeAnalyzerDb;
GO

IF OBJECT_ID(N'dbo.platforms', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.platforms (
        platform_id BIGINT IDENTITY(1,1) NOT NULL,
        code NVARCHAR(50) NOT NULL,
        name NVARCHAR(100) NOT NULL,
        base_url NVARCHAR(255) NULL,
        api_url NVARCHAR(255) NULL,
        is_active BIT NOT NULL CONSTRAINT DF_platforms_is_active DEFAULT (1),
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_platforms_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_platforms_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_platforms PRIMARY KEY CLUSTERED (platform_id),
        CONSTRAINT UQ_platforms_code UNIQUE (code)
    );
END
GO

IF OBJECT_ID(N'dbo.programming_handles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.programming_handles (
        handle_id BIGINT IDENTITY(1,1) NOT NULL,
        platform_id BIGINT NOT NULL,
        handle NVARCHAR(100) NOT NULL,
        display_name NVARCHAR(150) NULL,
        group_name NVARCHAR(100) NULL,
        rating INT NULL,
        rank_name NVARCHAR(50) NULL,
        general_evaluation NVARCHAR(MAX) NULL,
        consent_status VARCHAR(30) NOT NULL CONSTRAINT DF_programming_handles_consent DEFAULT ('UNKNOWN'),
        is_active BIT NOT NULL CONSTRAINT DF_programming_handles_is_active DEFAULT (1),
        last_crawled_at DATETIME2(0) NULL,
        notes NVARCHAR(500) NULL,
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_programming_handles_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_programming_handles_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_programming_handles PRIMARY KEY CLUSTERED (handle_id),
        CONSTRAINT FK_programming_handles_platform FOREIGN KEY (platform_id) REFERENCES dbo.platforms(platform_id),
        CONSTRAINT UQ_programming_handles_platform_handle UNIQUE (platform_id, handle),
        CONSTRAINT CK_programming_handles_rating CHECK (rating IS NULL OR rating >= 0)
    );
END
GO

IF COL_LENGTH('dbo.programming_handles', 'rating') IS NULL
BEGIN
    ALTER TABLE dbo.programming_handles
    ADD rating INT NULL;
END
GO

IF COL_LENGTH('dbo.programming_handles', 'rank_name') IS NULL
BEGIN
    ALTER TABLE dbo.programming_handles
    ADD rank_name NVARCHAR(50) NULL;
END
GO

IF COL_LENGTH('dbo.programming_handles', 'general_evaluation') IS NULL
BEGIN
    ALTER TABLE dbo.programming_handles
    ADD general_evaluation NVARCHAR(MAX) NULL;
END
GO

IF OBJECT_ID(N'dbo.CK_programming_handles_rating', N'C') IS NULL
BEGIN
    ALTER TABLE dbo.programming_handles WITH CHECK
    ADD CONSTRAINT CK_programming_handles_rating CHECK (rating IS NULL OR rating >= 0);
END
GO

IF OBJECT_ID(N'dbo.submissions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.submissions (
        submission_id BIGINT IDENTITY(1,1) NOT NULL,
        platform_id BIGINT NOT NULL,
        handle_id BIGINT NOT NULL,
        platform_submission_id NVARCHAR(100) NOT NULL,
        problem_code NVARCHAR(100) NULL,
        problem_name NVARCHAR(255) NULL,
        contest_id NVARCHAR(100) NULL,
        language NVARCHAR(100) NULL,
        verdict VARCHAR(50) NULL,
        submitted_at DATETIME2(0) NULL,
        execution_time_ms INT NULL,
        memory_bytes BIGINT NULL,
        problem_rating INT NULL,
        problem_tags NVARCHAR(1000) NULL,
        source_url NVARCHAR(500) NULL,
        source_crawl_status VARCHAR(30) NOT NULL CONSTRAINT DF_submissions_source_crawl_status DEFAULT ('PENDING'),
        source_crawled_at DATETIME2(0) NULL,
        source_crawl_error NVARCHAR(1000) NULL,
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_submissions_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_submissions_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_submissions PRIMARY KEY CLUSTERED (submission_id),
        CONSTRAINT FK_submissions_platform FOREIGN KEY (platform_id) REFERENCES dbo.platforms(platform_id),
        CONSTRAINT FK_submissions_handle FOREIGN KEY (handle_id) REFERENCES dbo.programming_handles(handle_id),
        CONSTRAINT UQ_submissions_platform_remote UNIQUE (platform_id, platform_submission_id),
        CONSTRAINT CK_submissions_source_crawl_status CHECK (
            source_crawl_status IN ('PENDING', 'CRAWLED', 'FAILED', 'SKIPPED')
        )
    );
END
GO

IF COL_LENGTH('dbo.submissions', 'source_crawl_status') IS NULL
BEGIN
    ALTER TABLE dbo.submissions
    ADD source_crawl_status VARCHAR(30) NOT NULL
        CONSTRAINT DF_submissions_source_crawl_status DEFAULT ('PENDING') WITH VALUES;
END
GO

IF COL_LENGTH('dbo.submissions', 'source_crawled_at') IS NULL
BEGIN
    ALTER TABLE dbo.submissions
    ADD source_crawled_at DATETIME2(0) NULL;
END
GO

IF COL_LENGTH('dbo.submissions', 'source_crawl_error') IS NULL
BEGIN
    ALTER TABLE dbo.submissions
    ADD source_crawl_error NVARCHAR(1000) NULL;
END
GO

IF OBJECT_ID(N'dbo.CK_submissions_source_crawl_status', N'C') IS NULL
BEGIN
    ALTER TABLE dbo.submissions WITH CHECK
    ADD CONSTRAINT CK_submissions_source_crawl_status
        CHECK (source_crawl_status IN ('PENDING', 'CRAWLED', 'FAILED', 'SKIPPED'));
END
GO

IF OBJECT_ID(N'dbo.source_codes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.source_codes (
        source_code_id BIGINT IDENTITY(1,1) NOT NULL,
        submission_id BIGINT NOT NULL,
        code_content NVARCHAR(MAX) NULL,
        code_hash VARCHAR(64) NULL,
        line_count INT NULL,
        char_count INT NULL,
        fetched_at DATETIME2(0) NULL,
        storage_type VARCHAR(50) NOT NULL CONSTRAINT DF_source_codes_storage_type DEFAULT ('DATABASE'),
        is_encrypted BIT NOT NULL CONSTRAINT DF_source_codes_is_encrypted DEFAULT (0),
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_source_codes_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_source_codes_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_source_codes PRIMARY KEY CLUSTERED (source_code_id),
        CONSTRAINT FK_source_codes_submission FOREIGN KEY (submission_id) REFERENCES dbo.submissions(submission_id),
        CONSTRAINT UQ_source_codes_submission UNIQUE (submission_id)
    );
END
GO

IF OBJECT_ID(N'dbo.ai_analysis_results', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ai_analysis_results (
        analysis_id BIGINT IDENTITY(1,1) NOT NULL,
        submission_id BIGINT NOT NULL,
        analyzer_type VARCHAR(50) NOT NULL,
        analyzer_version VARCHAR(50) NULL,
        model_name NVARCHAR(100) NULL,
        data_structures NVARCHAR(1000) NULL,
        algorithms NVARCHAR(1000) NULL,
        complexity_estimate NVARCHAR(255) NULL,
        code_quality_score DECIMAL(5,2) NULL,
        ai_risk_score DECIMAL(5,2) NULL,
        ai_risk_level VARCHAR(30) NULL,
        summary NVARCHAR(MAX) NULL,
        raw_response NVARCHAR(MAX) NULL,
        prompt_hash VARCHAR(64) NULL,
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_ai_analysis_results_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_ai_analysis_results_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_ai_analysis_results PRIMARY KEY CLUSTERED (analysis_id),
        CONSTRAINT FK_ai_analysis_results_submission FOREIGN KEY (submission_id) REFERENCES dbo.submissions(submission_id),
        CONSTRAINT CK_ai_analysis_results_code_quality_score CHECK (code_quality_score IS NULL OR code_quality_score BETWEEN 0 AND 100),
        CONSTRAINT CK_ai_analysis_results_ai_risk_score CHECK (ai_risk_score IS NULL OR ai_risk_score BETWEEN 0 AND 100)
    );
END
GO

IF OBJECT_ID(N'dbo.user_skill_scores', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_skill_scores (
        score_id BIGINT IDENTITY(1,1) NOT NULL,
        handle_id BIGINT NOT NULL,
        period_start DATE NOT NULL,
        period_end DATE NOT NULL,
        data_structure_score DECIMAL(5,2) NULL,
        algorithm_score DECIMAL(5,2) NULL,
        problem_solving_score DECIMAL(5,2) NULL,
        code_quality_score DECIMAL(5,2) NULL,
        practice_consistency_score DECIMAL(5,2) NULL,
        ai_usage_risk_score DECIMAL(5,2) NULL,
        overall_score DECIMAL(5,2) NULL,
        summary NVARCHAR(MAX) NULL,
        generated_at DATETIME2(0) NOT NULL CONSTRAINT DF_user_skill_scores_generated_at DEFAULT SYSUTCDATETIME(),
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_user_skill_scores_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_user_skill_scores_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_user_skill_scores PRIMARY KEY CLUSTERED (score_id),
        CONSTRAINT FK_user_skill_scores_handle FOREIGN KEY (handle_id) REFERENCES dbo.programming_handles(handle_id),
        CONSTRAINT UQ_user_skill_scores_handle_period UNIQUE (handle_id, period_start, period_end),
        CONSTRAINT CK_user_skill_scores_data_structure CHECK (data_structure_score IS NULL OR data_structure_score BETWEEN 0 AND 100),
        CONSTRAINT CK_user_skill_scores_algorithm CHECK (algorithm_score IS NULL OR algorithm_score BETWEEN 0 AND 100),
        CONSTRAINT CK_user_skill_scores_problem_solving CHECK (problem_solving_score IS NULL OR problem_solving_score BETWEEN 0 AND 100),
        CONSTRAINT CK_user_skill_scores_code_quality CHECK (code_quality_score IS NULL OR code_quality_score BETWEEN 0 AND 100),
        CONSTRAINT CK_user_skill_scores_consistency CHECK (practice_consistency_score IS NULL OR practice_consistency_score BETWEEN 0 AND 100),
        CONSTRAINT CK_user_skill_scores_ai_risk CHECK (ai_usage_risk_score IS NULL OR ai_usage_risk_score BETWEEN 0 AND 100),
        CONSTRAINT CK_user_skill_scores_overall CHECK (overall_score IS NULL OR overall_score BETWEEN 0 AND 100)
    );
END
GO

IF OBJECT_ID(N'dbo.crawl_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.crawl_logs (
        crawl_log_id BIGINT IDENTITY(1,1) NOT NULL,
        job_type VARCHAR(30) NOT NULL,
        status VARCHAR(30) NOT NULL,
        started_at DATETIME2(0) NULL,
        finished_at DATETIME2(0) NULL,
        total_handles INT NOT NULL CONSTRAINT DF_crawl_logs_total_handles DEFAULT (0),
        total_new_submissions INT NOT NULL CONSTRAINT DF_crawl_logs_total_new DEFAULT (0),
        total_errors INT NOT NULL CONSTRAINT DF_crawl_logs_total_errors DEFAULT (0),
        message NVARCHAR(MAX) NULL,
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_crawl_logs_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_crawl_logs_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_crawl_logs PRIMARY KEY CLUSTERED (crawl_log_id)
    );
END
GO

IF OBJECT_ID(N'dbo.app_settings', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.app_settings (
        setting_key NVARCHAR(100) NOT NULL,
        setting_value NVARCHAR(1000) NULL,
        description NVARCHAR(500) NULL,
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_app_settings_created_at DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_app_settings_updated_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_app_settings PRIMARY KEY CLUSTERED (setting_key)
    );
END
GO

MERGE dbo.app_settings AS target
USING (VALUES
    (N'scheduler.auto-crawl-enabled', N'false', N'Enable or disable ScheduledExecutorService daily crawl for new submissions.'),
    (N'scheduler.daily-run-time', N'01:00', N'Daily crawl run time in HH:mm local time.')
) AS source(setting_key, setting_value, description)
ON target.setting_key = source.setting_key
WHEN NOT MATCHED THEN
    INSERT (setting_key, setting_value, description)
    VALUES (source.setting_key, source.setting_value, source.description);
GO

DELETE FROM dbo.app_settings
WHERE setting_key = N'scheduler.workflow-interval-hours';
GO

IF OBJECT_ID(N'dbo.error_logs', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.error_logs (
        error_log_id BIGINT IDENTITY(1,1) NOT NULL,
        component NVARCHAR(100) NOT NULL,
        severity VARCHAR(20) NOT NULL,
        sanitized_message NVARCHAR(MAX) NOT NULL,
        stack_trace NVARCHAR(MAX) NULL,
        created_at DATETIME2(0) NOT NULL CONSTRAINT DF_error_logs_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_error_logs PRIMARY KEY CLUSTERED (error_log_id)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_programming_handles_platform' AND object_id = OBJECT_ID(N'dbo.programming_handles'))
    CREATE INDEX IX_programming_handles_platform ON dbo.programming_handles(platform_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_submissions_handle_id' AND object_id = OBJECT_ID(N'dbo.submissions'))
    CREATE INDEX IX_submissions_handle_id ON dbo.submissions(handle_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_submissions_platform_id' AND object_id = OBJECT_ID(N'dbo.submissions'))
    CREATE INDEX IX_submissions_platform_id ON dbo.submissions(platform_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_submissions_platform_submission_id' AND object_id = OBJECT_ID(N'dbo.submissions'))
    CREATE INDEX IX_submissions_platform_submission_id ON dbo.submissions(platform_submission_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_submissions_handle_submitted' AND object_id = OBJECT_ID(N'dbo.submissions'))
    CREATE INDEX IX_submissions_handle_submitted ON dbo.submissions(handle_id, submitted_at DESC, submission_id DESC);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_submissions_submitted_recent' AND object_id = OBJECT_ID(N'dbo.submissions'))
    CREATE INDEX IX_submissions_submitted_recent ON dbo.submissions(submitted_at DESC, submission_id DESC);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_submissions_source_crawl_status' AND object_id = OBJECT_ID(N'dbo.submissions'))
    CREATE INDEX IX_submissions_source_crawl_status ON dbo.submissions(source_crawl_status, created_at ASC);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_source_codes_submission_id' AND object_id = OBJECT_ID(N'dbo.source_codes'))
    CREATE INDEX IX_source_codes_submission_id ON dbo.source_codes(submission_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ai_analysis_results_submission_id' AND object_id = OBJECT_ID(N'dbo.ai_analysis_results'))
    CREATE INDEX IX_ai_analysis_results_submission_id ON dbo.ai_analysis_results(submission_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_ai_analysis_results_submission_created' AND object_id = OBJECT_ID(N'dbo.ai_analysis_results'))
    CREATE INDEX IX_ai_analysis_results_submission_created ON dbo.ai_analysis_results(submission_id, created_at DESC, analysis_id DESC);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_user_skill_scores_handle_id' AND object_id = OBJECT_ID(N'dbo.user_skill_scores'))
    CREATE INDEX IX_user_skill_scores_handle_id ON dbo.user_skill_scores(handle_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_user_skill_scores_handle_period' AND object_id = OBJECT_ID(N'dbo.user_skill_scores'))
    CREATE INDEX IX_user_skill_scores_handle_period ON dbo.user_skill_scores(handle_id, period_end DESC, period_start DESC, generated_at DESC);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_crawl_logs_started_at' AND object_id = OBJECT_ID(N'dbo.crawl_logs'))
    CREATE INDEX IX_crawl_logs_started_at ON dbo.crawl_logs(started_at DESC);
GO

-- ============================================================
-- Seed data
-- ============================================================

USE CodeAnalyzerDb;
GO

IF NOT EXISTS (SELECT 1 FROM dbo.platforms WHERE code = N'CODEFORCES')
BEGIN
    INSERT INTO dbo.platforms (code, name, base_url, api_url)
    VALUES (N'CODEFORCES', N'Codeforces', N'https://codeforces.com', N'https://codeforces.com/api');
END

IF NOT EXISTS (SELECT 1 FROM dbo.platforms WHERE code = N'VJUDGE')
BEGIN
    INSERT INTO dbo.platforms (code, name, base_url, api_url)
    VALUES (N'VJUDGE', N'VJudge', N'https://vjudge.net', NULL);
END
GO

DECLARE @CodeforcesId BIGINT = (SELECT platform_id FROM dbo.platforms WHERE code = N'CODEFORCES');
DECLARE @VJudgeId BIGINT = (SELECT platform_id FROM dbo.platforms WHERE code = N'VJUDGE');

IF NOT EXISTS (SELECT 1 FROM dbo.programming_handles WHERE platform_id = @CodeforcesId AND handle = N'demo_cf_alpha')
    INSERT INTO dbo.programming_handles (platform_id, handle, display_name, group_name, consent_status, notes)
    VALUES (@CodeforcesId, N'demo_cf_alpha', N'Demo CF Alpha', N'DEMO', 'PUBLIC', N'Dữ liệu demo, không khẳng định là dữ liệu thật.');

IF NOT EXISTS (SELECT 1 FROM dbo.programming_handles WHERE platform_id = @CodeforcesId AND handle = N'demo_cf_beta')
    INSERT INTO dbo.programming_handles (platform_id, handle, display_name, group_name, consent_status, notes)
    VALUES (@CodeforcesId, N'demo_cf_beta', N'Demo CF Beta', N'DEMO', 'PUBLIC', N'Dữ liệu demo, không khẳng định là dữ liệu thật.');

IF NOT EXISTS (SELECT 1 FROM dbo.programming_handles WHERE platform_id = @CodeforcesId AND handle = N'demo_cf_gamma')
    INSERT INTO dbo.programming_handles (platform_id, handle, display_name, group_name, consent_status, notes)
    VALUES (@CodeforcesId, N'demo_cf_gamma', N'Demo CF Gamma', N'DEMO', 'PUBLIC', N'Dữ liệu demo, không khẳng định là dữ liệu thật.');

IF NOT EXISTS (SELECT 1 FROM dbo.programming_handles WHERE platform_id = @VJudgeId AND handle = N'demo_vj_delta')
    INSERT INTO dbo.programming_handles (platform_id, handle, display_name, group_name, consent_status, notes)
    VALUES (@VJudgeId, N'demo_vj_delta', N'Demo VJ Delta', N'DEMO', 'AUTHORIZED_IMPORT', N'Dữ liệu demo import, không khẳng định là dữ liệu thật.');

IF NOT EXISTS (SELECT 1 FROM dbo.programming_handles WHERE platform_id = @VJudgeId AND handle = N'demo_vj_epsilon')
    INSERT INTO dbo.programming_handles (platform_id, handle, display_name, group_name, consent_status, notes)
    VALUES (@VJudgeId, N'demo_vj_epsilon', N'Demo VJ Epsilon', N'DEMO', 'AUTHORIZED_IMPORT', N'Dữ liệu demo import, không khẳng định là dữ liệu thật.');

UPDATE dbo.programming_handles
SET rating = source.rating,
    rank_name = source.rank_name,
    general_evaluation = source.general_evaluation,
    updated_at = SYSUTCDATETIME()
FROM dbo.programming_handles target
JOIN (VALUES
    (@CodeforcesId, N'demo_cf_alpha', 1150, N'pupil', N'Nền tảng implementation ổn, cần tăng độ khó bài luyện.'),
    (@CodeforcesId, N'demo_cf_beta', 1420, N'specialist', N'Có năng lực greedy/sorting tốt, cần cải thiện kiểm thử biên.'),
    (@CodeforcesId, N'demo_cf_gamma', 1680, N'expert', N'Khá mạnh ở prefix sum, binary search và greedy.'),
    (@VJudgeId, N'demo_vj_delta', 1500, N'tracked', N'Có nền tảng graph, BFS, Dijkstra tốt.'),
    (@VJudgeId, N'demo_vj_epsilon', 1600, N'tracked', N'Bao phủ tốt DSU, BFS, DP và binary search.')
) AS source(platform_id, handle, rating, rank_name, general_evaluation)
    ON source.platform_id = target.platform_id AND source.handle = target.handle;
GO

DECLARE @CodeforcesId BIGINT = (SELECT platform_id FROM dbo.platforms WHERE code = N'CODEFORCES');
DECLARE @VJudgeId BIGINT = (SELECT platform_id FROM dbo.platforms WHERE code = N'VJUDGE');
DECLARE @Alpha BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE platform_id = @CodeforcesId AND handle = N'demo_cf_alpha');
DECLARE @Beta BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE platform_id = @CodeforcesId AND handle = N'demo_cf_beta');
DECLARE @Gamma BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE platform_id = @CodeforcesId AND handle = N'demo_cf_gamma');
DECLARE @Delta BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE platform_id = @VJudgeId AND handle = N'demo_vj_delta');
DECLARE @Epsilon BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE platform_id = @VJudgeId AND handle = N'demo_vj_epsilon');

MERGE dbo.submissions AS target
USING (VALUES
    (@CodeforcesId, @Alpha, N'CF-DEMO-001', N'1703A', N'YES or YES?', N'1703', N'GNU C++17', 'OK', '2026-05-01T08:10:00', 46, 102400, 800, N'implementation,strings', N'https://codeforces.com/contest/1703/submission/CF-DEMO-001'),
    (@CodeforcesId, @Alpha, N'CF-DEMO-002', N'1692B', N'All Distinct', N'1692', N'GNU C++17', 'OK', '2026-05-02T09:20:00', 62, 204800, 800, N'sortings,greedy', N'https://codeforces.com/contest/1692/submission/CF-DEMO-002'),
    (@CodeforcesId, @Alpha, N'CF-DEMO-003', N'1676C', N'Most Similar Words', N'1676', N'Java 21', 'OK', '2026-05-03T10:30:00', 93, 262144, 1000, N'brute force,strings', N'https://codeforces.com/contest/1676/submission/CF-DEMO-003'),
    (@CodeforcesId, @Alpha, N'CF-DEMO-004', N'1669G', N'Fall Down', N'1669', N'GNU C++17', 'WRONG_ANSWER', '2026-05-04T11:40:00', 124, 524288, 1100, N'implementation,simulation', N'https://codeforces.com/contest/1669/submission/CF-DEMO-004'),
    (@CodeforcesId, @Beta, N'CF-DEMO-005', N'1650C', N'Weight of the System', N'1650', N'GNU C++17', 'OK', '2026-05-05T12:00:00', 218, 1048576, 1300, N'sortings,greedy', N'https://codeforces.com/contest/1650/submission/CF-DEMO-005'),
    (@CodeforcesId, @Beta, N'CF-DEMO-006', N'1638B', N'Odd Swap Sort', N'1638', N'PyPy 3', 'OK', '2026-05-06T12:30:00', 171, 262144, 1200, N'sortings', N'https://codeforces.com/contest/1638/submission/CF-DEMO-006'),
    (@CodeforcesId, @Beta, N'CF-DEMO-007', N'1624C', N'Division by Two and Permutation', N'1624', N'GNU C++17', 'OK', '2026-05-07T13:00:00', 187, 1048576, 1400, N'greedy,sets', N'https://codeforces.com/contest/1624/submission/CF-DEMO-007'),
    (@CodeforcesId, @Beta, N'CF-DEMO-008', N'1607D', N'Blue-Red Permutation', N'1607', N'Java 21', 'TIME_LIMIT_EXCEEDED', '2026-05-08T13:30:00', 2000, 1048576, 1400, N'greedy,sortings', N'https://codeforces.com/contest/1607/submission/CF-DEMO-008'),
    (@CodeforcesId, @Gamma, N'CF-DEMO-009', N'1593C', N'Save More Mice', N'1593', N'GNU C++17', 'OK', '2026-05-09T14:00:00', 140, 524288, 1200, N'greedy,sortings', N'https://codeforces.com/contest/1593/submission/CF-DEMO-009'),
    (@CodeforcesId, @Gamma, N'CF-DEMO-010', N'1555C', N'Coin Rows', N'1555', N'GNU C++17', 'OK', '2026-05-10T14:30:00', 93, 262144, 1300, N'dp,prefix sums', N'https://codeforces.com/contest/1555/submission/CF-DEMO-010'),
    (@CodeforcesId, @Gamma, N'CF-DEMO-011', N'1535B', N'Array Reodering', N'1535', N'PyPy 3', 'OK', '2026-05-11T15:00:00', 125, 262144, 1000, N'math,greedy', N'https://codeforces.com/contest/1535/submission/CF-DEMO-011'),
    (@CodeforcesId, @Gamma, N'CF-DEMO-012', N'1490G', N'Old Floppy Drive', N'1490', N'GNU C++17', 'OK', '2026-05-12T15:30:00', 312, 1048576, 1600, N'binary search,prefix sums', N'https://codeforces.com/contest/1490/submission/CF-DEMO-012'),
    (@VJudgeId, @Delta, N'VJ-DEMO-001', N'HDu-1003', N'Max Sum', N'HDU', N'Java 21', 'OK', '2026-05-01T16:00:00', 156, 524288, 1000, N'dp,kadane', N'https://vjudge.net/solution/VJ-DEMO-001'),
    (@VJudgeId, @Delta, N'VJ-DEMO-002', N'POJ-3255', N'Roadblocks', N'POJ', N'GNU C++17', 'OK', '2026-05-02T16:30:00', 390, 2097152, 1600, N'graph,dijkstra,priority queue', N'https://vjudge.net/solution/VJ-DEMO-002'),
    (@VJudgeId, @Delta, N'VJ-DEMO-003', N'UVA-10004', N'Bicoloring', N'UVA', N'GNU C++17', 'OK', '2026-05-03T17:00:00', 62, 262144, 1200, N'graph,bfs', N'https://vjudge.net/solution/VJ-DEMO-003'),
    (@VJudgeId, @Delta, N'VJ-DEMO-004', N'LightOJ-1002', N'Country Roads', N'LightOJ', N'GNU C++17', 'WRONG_ANSWER', '2026-05-04T17:30:00', 421, 2097152, 1500, N'graph,dijkstra', N'https://vjudge.net/solution/VJ-DEMO-004'),
    (@VJudgeId, @Epsilon, N'VJ-DEMO-005', N'SPOJ-AGGRCOW', N'Aggressive Cows', N'SPOJ', N'GNU C++17', 'OK', '2026-05-05T18:00:00', 109, 524288, 1400, N'binary search', N'https://vjudge.net/solution/VJ-DEMO-005'),
    (@VJudgeId, @Epsilon, N'VJ-DEMO-006', N'CSES-1193', N'Labyrinth', N'CSES', N'GNU C++17', 'OK', '2026-05-06T18:30:00', 187, 1048576, 1200, N'graph,bfs,queue', N'https://vjudge.net/solution/VJ-DEMO-006'),
    (@VJudgeId, @Epsilon, N'VJ-DEMO-007', N'CSES-1633', N'Dice Combinations', N'CSES', N'PyPy 3', 'OK', '2026-05-07T19:00:00', 203, 262144, 1100, N'dp', N'https://vjudge.net/solution/VJ-DEMO-007'),
    (@VJudgeId, @Epsilon, N'VJ-DEMO-008', N'CSES-1666', N'Building Roads', N'CSES', N'Java 21', 'OK', '2026-05-08T19:30:00', 250, 1048576, 1300, N'dsu,graph', N'https://vjudge.net/solution/VJ-DEMO-008')
) AS source (platform_id, handle_id, platform_submission_id, problem_code, problem_name, contest_id, language, verdict, submitted_at, execution_time_ms, memory_bytes, problem_rating, problem_tags, source_url)
ON target.platform_id = source.platform_id AND target.platform_submission_id = source.platform_submission_id
WHEN NOT MATCHED THEN
    INSERT (platform_id, handle_id, platform_submission_id, problem_code, problem_name, contest_id, language, verdict, submitted_at, execution_time_ms, memory_bytes, problem_rating, problem_tags, source_url)
    VALUES (source.platform_id, source.handle_id, source.platform_submission_id, source.problem_code, source.problem_name, source.contest_id, source.language, source.verdict, source.submitted_at, source.execution_time_ms, source.memory_bytes, source.problem_rating, source.problem_tags, source.source_url);
GO

DECLARE @CodeforcesId BIGINT = (SELECT platform_id FROM dbo.platforms WHERE code = N'CODEFORCES');
DECLARE @VJudgeId BIGINT = (SELECT platform_id FROM dbo.platforms WHERE code = N'VJUDGE');

INSERT INTO dbo.source_codes (submission_id, code_content, code_hash, line_count, char_count, fetched_at, storage_type, is_encrypted)
SELECT s.submission_id,
       source.code_content,
       CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), source.code_content)), 2),
       1,
       LEN(source.code_content),
       SYSUTCDATETIME(),
       'DATABASE',
       0
FROM (VALUES
    (@CodeforcesId, N'CF-DEMO-001', N'#include <bits/stdc++.h> using namespace std; int main(){string s;cin>>s; for(char &c:s)c=toupper(c); cout<<(s=="YES"?"YES":"NO");}'),
    (@CodeforcesId, N'CF-DEMO-002', N'#include <bits/stdc++.h> using namespace std; int main(){int n;cin>>n; vector<int>a(n); set<int> st; for(int &x:a){cin>>x; st.insert(x);} cout<<st.size();}'),
    (@CodeforcesId, N'CF-DEMO-003', N'import java.util.*; class Main{public static void main(String[]a){Scanner sc=new Scanner(System.in); int n=sc.nextInt(); System.out.println(n);}}'),
    (@CodeforcesId, N'CF-DEMO-004', N'#include <bits/stdc++.h> using namespace std; int main(){int n,m;cin>>n>>m; vector<string> g(n); for(auto &r:g)cin>>r; cout<<g[0];}'),
    (@CodeforcesId, N'CF-DEMO-005', N'#include <bits/stdc++.h> using namespace std; int main(){int n;cin>>n; vector<pair<int,int>> a(n); sort(a.begin(),a.end()); cout<<n;}'),
    (@CodeforcesId, N'CF-DEMO-006', N'n=int(input()); a=list(map(int,input().split())); print("YES" if a==sorted(a) else "NO")'),
    (@CodeforcesId, N'CF-DEMO-007', N'#include <bits/stdc++.h> using namespace std; int main(){priority_queue<int> pq; int n,x;cin>>n; while(n--){cin>>x; pq.push(x);} cout<<pq.top();}'),
    (@CodeforcesId, N'CF-DEMO-008', N'import java.util.*; class Main{public static void main(String[]args){Scanner sc=new Scanner(System.in); int n=sc.nextInt(); int[] a=new int[n]; Arrays.sort(a);}}'),
    (@CodeforcesId, N'CF-DEMO-009', N'#include <bits/stdc++.h> using namespace std; int main(){vector<int>a={1,2,3}; sort(a.rbegin(),a.rend()); cout<<a.size();}'),
    (@CodeforcesId, N'CF-DEMO-010', N'#include <bits/stdc++.h> using namespace std; int main(){long long pref=0,best=1e18,x; while(cin>>x){pref+=x; best=min(best,pref);} cout<<best;}'),
    (@CodeforcesId, N'CF-DEMO-011', N'a=list(map(int,input().split())); print(sum(1 for x in a if x%2==0))'),
    (@CodeforcesId, N'CF-DEMO-012', N'#include <bits/stdc++.h> using namespace std; int main(){vector<int>a={1,3,5}; cout<<binary_search(a.begin(),a.end(),3);}'),
    (@VJudgeId, N'VJ-DEMO-001', N'import java.util.*; class Main{public static void main(String[]args){int[] dp={1,2,3}; System.out.println(dp[0]);}}'),
    (@VJudgeId, N'VJ-DEMO-002', N'#include <bits/stdc++.h> using namespace std; int main(){priority_queue<pair<int,int>,vector<pair<int,int>>,greater<pair<int,int>>> pq; cout<<pq.size();}'),
    (@VJudgeId, N'VJ-DEMO-003', N'#include <bits/stdc++.h> using namespace std; int main(){queue<int> q; q.push(1); while(!q.empty()) q.pop();}'),
    (@VJudgeId, N'VJ-DEMO-004', N'#include <bits/stdc++.h> using namespace std; int main(){map<int,vector<int>> g; g[1].push_back(2); cout<<g.size();}'),
    (@VJudgeId, N'VJ-DEMO-005', N'#include <bits/stdc++.h> using namespace std; bool ok(int x){return x>0;} int main(){int l=0,r=10; while(l<r){int m=(l+r)/2; if(ok(m)) r=m; else l=m+1;} cout<<l;}'),
    (@VJudgeId, N'VJ-DEMO-006', N'#include <bits/stdc++.h> using namespace std; int main(){queue<pair<int,int>> q; q.push({0,0}); cout<<q.front().first;}'),
    (@VJudgeId, N'VJ-DEMO-007', N'n=int(input()); dp=[0]*(n+1); dp[0]=1; print(dp[0])'),
    (@VJudgeId, N'VJ-DEMO-008', N'import java.util.*; class Main{static int find(int[] p,int x){return p[x]==x?x:find(p,p[x]);} public static void main(String[]a){int[] p={0,1};}}')
) AS source(platform_id, platform_submission_id, code_content)
JOIN dbo.submissions s ON s.platform_id = source.platform_id AND s.platform_submission_id = source.platform_submission_id
WHERE NOT EXISTS (SELECT 1 FROM dbo.source_codes sc WHERE sc.submission_id = s.submission_id);
GO

INSERT INTO dbo.ai_analysis_results (
    submission_id, analyzer_type, analyzer_version, model_name, data_structures, algorithms,
    complexity_estimate, code_quality_score, ai_risk_score, ai_risk_level, summary, raw_response, prompt_hash
)
SELECT s.submission_id, 'MOCK_AI', 'demo-1.0', 'mock-demo', source.data_structures, source.algorithms,
       source.complexity_estimate, source.code_quality_score, source.ai_risk_score, source.ai_risk_level,
       source.summary, N'{"demo":true}', source.prompt_hash
FROM (VALUES
    (N'CF-DEMO-001', N'string', N'implementation', N'O(n)', 72, 12, 'LOW', N'Nhận diện thao tác chuỗi cơ bản, phù hợp mức nhập môn.', 'demo-CF-DEMO-001'),
    (N'CF-DEMO-002', N'vector,set', N'sorting,greedy', N'O(n log n)', 76, 18, 'LOW', N'Có sử dụng set và tư duy loại trùng đơn giản.', 'demo-CF-DEMO-002'),
    (N'CF-DEMO-003', N'array', N'brute force', N'O(n)', 64, 22, 'LOW', N'Code ngắn, confidence thấp do ít ngữ cảnh.', 'demo-CF-DEMO-003'),
    (N'CF-DEMO-004', N'grid,vector', N'simulation', N'O(nm)', 58, 34, 'MEDIUM', N'Lời giải mô phỏng còn thiếu kiểm soát biên.', 'demo-CF-DEMO-004'),
    (N'CF-DEMO-005', N'vector,pair', N'sorting,greedy', N'O(n log n)', 82, 20, 'LOW', N'Cấu trúc lời giải rõ, dùng sorting hợp lý.', 'demo-CF-DEMO-005'),
    (N'CF-DEMO-006', N'list', N'sorting', N'O(n log n)', 70, 28, 'LOW', N'Lời giải Python ngắn gọn, phù hợp bài kiểm tra sắp xếp.', 'demo-CF-DEMO-006'),
    (N'CF-DEMO-007', N'priority_queue', N'greedy', N'O(n log n)', 79, 36, 'MEDIUM', N'Có dùng hàng đợi ưu tiên, cần kiểm chứng thêm với lịch sử làm bài.', 'demo-CF-DEMO-007'),
    (N'CF-DEMO-008', N'array', N'sorting', N'O(n log n)', 55, 40, 'MEDIUM', N'Ý tưởng đúng hướng nhưng kết quả TLE, cần tối ưu I/O hoặc thuật toán.', 'demo-CF-DEMO-008'),
    (N'CF-DEMO-009', N'vector', N'sorting,greedy', N'O(n log n)', 81, 16, 'LOW', N'Triển khai greedy/sorting ổn định.', 'demo-CF-DEMO-009'),
    (N'CF-DEMO-010', N'array,prefix sum', N'dynamic programming,prefix sums', N'O(n)', 84, 24, 'LOW', N'Nhận diện prefix sum và quy hoạch động mức cơ bản.', 'demo-CF-DEMO-010'),
    (N'CF-DEMO-011', N'list', N'math,greedy', N'O(n)', 68, 26, 'LOW', N'Lời giải ngắn, phù hợp bài math/greedy dễ.', 'demo-CF-DEMO-011'),
    (N'CF-DEMO-012', N'vector', N'binary search,prefix sums', N'O(log n)', 88, 32, 'MEDIUM', N'Dùng binary search đúng mẫu, cần xem thêm lịch sử để đánh giá chắc hơn.', 'demo-CF-DEMO-012'),
    (N'VJ-DEMO-001', N'array,dp', N'dynamic programming', N'O(n)', 73, 18, 'LOW', N'Dấu hiệu DP cơ bản, phù hợp bài Max Sum.', 'demo-VJ-DEMO-001'),
    (N'VJ-DEMO-002', N'graph,priority_queue', N'dijkstra', N'O((V+E) log V)', 86, 30, 'MEDIUM', N'Có cấu trúc graph và priority queue cho shortest path.', 'demo-VJ-DEMO-002'),
    (N'VJ-DEMO-003', N'queue,graph', N'bfs', N'O(V+E)', 78, 14, 'LOW', N'BFS đơn giản, code dễ đọc.', 'demo-VJ-DEMO-003'),
    (N'VJ-DEMO-004', N'map,vector,graph', N'dijkstra,graph', N'O((V+E) log V)', 62, 44, 'MEDIUM', N'Ý tưởng graph rõ nhưng verdict chưa accepted, cần rà lỗi.', 'demo-VJ-DEMO-004'),
    (N'VJ-DEMO-005', N'array', N'binary search', N'O(n log C)', 80, 20, 'LOW', N'Dùng binary search trên đáp án đúng hướng.', 'demo-VJ-DEMO-005'),
    (N'VJ-DEMO-006', N'queue,grid', N'bfs,graph', N'O(nm)', 82, 22, 'LOW', N'BFS trên mê cung, cấu trúc hàng đợi phù hợp.', 'demo-VJ-DEMO-006'),
    (N'VJ-DEMO-007', N'array,dp', N'dynamic programming', N'O(n)', 74, 26, 'LOW', N'DP tuyến tính cơ bản, cần thêm test biên.', 'demo-VJ-DEMO-007'),
    (N'VJ-DEMO-008', N'array,dsu', N'dsu,graph', N'O(alpha(n))', 83, 35, 'MEDIUM', N'Có dấu hiệu dùng DSU; mức rủi ro chỉ là tham khảo cần kiểm chứng thêm.', 'demo-VJ-DEMO-008')
) AS source(platform_submission_id, data_structures, algorithms, complexity_estimate, code_quality_score, ai_risk_score, ai_risk_level, summary, prompt_hash)
JOIN dbo.submissions s ON s.platform_submission_id = source.platform_submission_id
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.ai_analysis_results ar
    WHERE ar.submission_id = s.submission_id AND ar.prompt_hash = source.prompt_hash
);
GO

DECLARE @Alpha BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE handle = N'demo_cf_alpha');
DECLARE @Beta BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE handle = N'demo_cf_beta');
DECLARE @Gamma BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE handle = N'demo_cf_gamma');
DECLARE @Delta BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE handle = N'demo_vj_delta');
DECLARE @Epsilon BIGINT = (SELECT handle_id FROM dbo.programming_handles WHERE handle = N'demo_vj_epsilon');

MERGE dbo.user_skill_scores AS target
USING (VALUES
    (@Alpha, '2026-05-01', '2026-05-31', 63, 66, 62, 68, 70, 22, 66, N'Mức khá. Điểm mạnh là bài implementation và sorting; nên luyện thêm graph và DP. AI usage risk thấp, chỉ dùng làm tín hiệu tham khảo.'),
    (@Beta, '2026-05-01', '2026-05-31', 72, 75, 70, 74, 68, 31, 73, N'Mức tốt. Có khả năng dùng greedy/sorting ổn; nên cải thiện xử lý hiệu năng và kiểm thử biên. Một số dấu hiệu cần kiểm chứng thêm.'),
    (@Gamma, '2026-05-01', '2026-05-31', 78, 82, 80, 79, 76, 25, 80, N'Mức tốt. Có tiến bộ ở prefix sum, binary search và greedy; nên mở rộng sang graph nâng cao.'),
    (@Delta, '2026-05-01', '2026-05-31', 84, 86, 82, 78, 74, 34, 82, N'Mức tốt. Có nền tảng graph, BFS, Dijkstra; cần củng cố debug với bài verdict chưa accepted.'),
    (@Epsilon, '2026-05-01', '2026-05-31', 86, 84, 83, 81, 79, 30, 84, N'Mức tốt. Có năng lực DSU, BFS, DP và binary search; nên luyện bài tổng hợp nhiều kỹ thuật.')
) AS source(handle_id, period_start, period_end, data_structure_score, algorithm_score, problem_solving_score, code_quality_score, practice_consistency_score, ai_usage_risk_score, overall_score, summary)
ON target.handle_id = source.handle_id AND target.period_start = source.period_start AND target.period_end = source.period_end
WHEN MATCHED THEN
    UPDATE SET data_structure_score = source.data_structure_score,
               algorithm_score = source.algorithm_score,
               problem_solving_score = source.problem_solving_score,
               code_quality_score = source.code_quality_score,
               practice_consistency_score = source.practice_consistency_score,
               ai_usage_risk_score = source.ai_usage_risk_score,
               overall_score = source.overall_score,
               summary = source.summary,
               generated_at = SYSUTCDATETIME(),
               updated_at = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (handle_id, period_start, period_end, data_structure_score, algorithm_score, problem_solving_score, code_quality_score, practice_consistency_score, ai_usage_risk_score, overall_score, summary)
    VALUES (source.handle_id, source.period_start, source.period_end, source.data_structure_score, source.algorithm_score, source.problem_solving_score, source.code_quality_score, source.practice_consistency_score, source.ai_usage_risk_score, source.overall_score, source.summary);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.crawl_logs WHERE message LIKE N'Demo seed data%')
BEGIN
    INSERT INTO dbo.crawl_logs (job_type, status, started_at, finished_at, total_handles, total_new_submissions, total_errors, message)
    VALUES ('MANUAL', 'SUCCESS', SYSUTCDATETIME(), SYSUTCDATETIME(), 5, 20, 0, N'Demo seed data inserted for dashboard, reports, and UI testing.');
END
GO

-- ============================================================
-- Consolidated legacy demo data from the old root sql folder
-- ============================================================

USE CodeAnalyzerDb;
GO

DECLARE @LegacyProfiles TABLE (
    platform_code NVARCHAR(50) NOT NULL,
    handle NVARCHAR(100) NOT NULL,
    display_name NVARCHAR(150) NOT NULL,
    group_name NVARCHAR(100) NOT NULL,
    submission_count INT NOT NULL,
    accepted_count INT NOT NULL,
    rating_base INT NULL,
    rating_step INT NULL,
    language NVARCHAR(100) NOT NULL,
    algorithms NVARCHAR(1000) NOT NULL,
    data_structures NVARCHAR(1000) NOT NULL,
    complexity_estimate NVARCHAR(255) NOT NULL,
    code_quality_score DECIMAL(5,2) NOT NULL,
    ai_risk_score DECIMAL(5,2) NOT NULL,
    ai_risk_level VARCHAR(30) NOT NULL,
    ds_score DECIMAL(5,2) NOT NULL,
    algorithm_score DECIMAL(5,2) NOT NULL,
    problem_solving_score DECIMAL(5,2) NOT NULL,
    consistency_score DECIMAL(5,2) NOT NULL,
    overall_score DECIMAL(5,2) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    remote_prefix NVARCHAR(30) NOT NULL,
    source_kind NVARCHAR(30) NOT NULL,
    summary NVARCHAR(MAX) NOT NULL
);

INSERT INTO @LegacyProfiles (
    platform_code, handle, display_name, group_name, submission_count, accepted_count,
    rating_base, rating_step, language, algorithms, data_structures, complexity_estimate,
    code_quality_score, ai_risk_score, ai_risk_level, ds_score, algorithm_score,
    problem_solving_score, consistency_score, overall_score, period_start, period_end,
    remote_prefix, source_kind, summary
)
VALUES
    (N'CODEFORCES', N'cf_ada_demo', N'Ada Demo', N'K17-CS', 4, 4, 800, 60, N'GNU C++17', N'implementation, sorting, greedy', N'array, vector, string', N'O(n log n)', 79, 19, 'LOW', 68, 64, 66, 88, 74, '2026-05-01', '2026-05-31', N'CF-ADA', N'SORTING', N'Stable beginner-to-intermediate progress. Strong implementation basics.'),
    (N'CODEFORCES', N'cf_bruno_demo', N'Bruno Demo', N'K17-CS', 4, 3, 1000, 80, N'GNU C++17', N'bfs, greedy, prefix sums, binary search', N'array, queue, set', N'O(n log n)', 78, 32, 'MEDIUM', 76, 73, 72, 70, 75, '2026-05-01', '2026-05-31', N'CF-BRU', N'BINARY_SEARCH', N'Good BFS, prefix sum and greedy coverage. Review binary search edge cases.'),
    (N'CODEFORCES', N'cf_celine_demo', N'Celine Demo', N'K17-ACM', 4, 3, 1300, 100, N'Java 21', N'dsu, dijkstra, segment tree, dynamic programming', N'graph, priority queue, segment tree, array', N'O((V+E) log V)', 78, 39, 'MEDIUM', 90, 84, 82, 64, 82, '2026-05-01', '2026-05-31', N'CF-CEL', N'GRAPH_DP', N'Strong advanced data structures and graph algorithms. Some style variance should be reviewed.'),
    (N'VJUDGE', N'vj_dan_demo', N'Dan Demo', N'K17-ACM', 4, 4, 900, 60, N'PyPy 3', N'simulation, string processing, dijkstra, binary search', N'list, graph, priority queue, array', N'O(n log n)', 77, 27, 'LOW', 74, 71, 72, 82, 76, '2026-05-01', '2026-05-31', N'VJ-DAN', N'PREFIX_GRAPH', N'Balanced practice on simulation, string processing, graph and binary search.'),
    (N'VJUDGE', N'vj_emi_demo', N'Emi Demo', N'K18-CS', 4, 3, 1100, 70, N'GNU C++17', N'fenwick tree, dynamic programming, bfs, string processing', N'fenwick tree, queue, graph, array', N'O(log n)', 80, 26, 'LOW', 83, 78, 79, 76, 80, '2026-05-01', '2026-05-31', N'VJ-EMI', N'DP_MIXED', N'Strong data structure coverage with Fenwick tree, DP and BFS examples.'),
    (N'CODEFORCES', N'demo_cf_nova', N'Nova Demo', N'DEMO-REPORT', 6, 3, 800, 70, N'GNU C++17', N'sorting, brute force', N'array, vector', N'O(n log n)', 58, 12, 'LOW', 38, 42, 41, 36, 42.50, '2026-04-26', '2026-05-13', N'DEMO-CF-NOVA', N'SORTING', N'Phân loại: Trung bình. Dữ liệu giả lập phục vụ báo cáo.'),
    (N'CODEFORCES', N'demo_cf_orion', N'Orion Demo', N'DEMO-REPORT', 9, 6, 1000, 90, N'Java 21', N'sorting, binary search, greedy', N'array, list, map', N'O(n log n)', 72, 28, 'LOW', 61, 66, 63, 57, 62.40, '2026-04-26', '2026-05-13', N'DEMO-CF-ORION', N'BINARY_SEARCH', N'Phân loại: Khá. Dữ liệu giả lập phục vụ báo cáo.'),
    (N'CODEFORCES', N'demo_cf_lyra', N'Lyra Demo', N'DEMO-REPORT', 15, 12, 1300, 110, N'GNU C++17', N'dfs, bfs, dijkstra, dynamic programming, greedy', N'vector, queue, priority queue, graph, matrix', N'O(V + E)', 86, 18, 'LOW', 84, 88, 82, 78, 82.70, '2026-04-26', '2026-05-13', N'DEMO-CF-LYRA', N'GRAPH_DP', N'Phân loại: Tốt. Dữ liệu giả lập phục vụ báo cáo.'),
    (N'VJUDGE', N'demo_vj_minh', N'Minh Demo', N'DEMO-REPORT', 11, 7, 900, 60, N'PyPy 3', N'greedy, prefix sum, dfs', N'array, stack, queue, graph', N'O(n)', 68, 46, 'MEDIUM', 64, 58, 60, 62, 58.80, '2026-04-26', '2026-05-13', N'DEMO-VJ-MINH', N'PREFIX_GRAPH', N'Phân loại: Khá. Dữ liệu giả lập phục vụ báo cáo.'),
    (N'VJUDGE', N'demo_vj_sora', N'Sora Demo', N'DEMO-REPORT', 18, 15, 1400, 95, N'GNU C++17', N'dynamic programming, graph traversal, binary search, greedy, sorting', N'vector, map, set, queue, priority queue, graph', N'O(n log n)', 90, 72, 'HIGH', 87, 91, 86, 80, 76.20, '2026-04-26', '2026-05-13', N'DEMO-VJ-SORA', N'DP_MIXED', N'Phân loại: Tốt. AI usage risk cần kiểm chứng thủ công; dữ liệu giả lập phục vụ báo cáo.');

INSERT INTO dbo.programming_handles (platform_id, handle, display_name, group_name, consent_status, is_active, last_crawled_at, notes)
SELECT p.platform_id, lp.handle, lp.display_name, lp.group_name, 'CONFIRMED', 1, '2026-05-13T09:00:00',
       N'DEMO ONLY - dữ liệu giả lập phục vụ dashboard/report.'
FROM @LegacyProfiles lp
JOIN dbo.platforms p ON p.code = lp.platform_code
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.programming_handles h
    WHERE h.platform_id = p.platform_id AND h.handle = lp.handle
);
GO

DECLARE @LegacyProfiles TABLE (
    platform_code NVARCHAR(50) NOT NULL,
    handle NVARCHAR(100) NOT NULL,
    submission_count INT NOT NULL,
    accepted_count INT NOT NULL,
    rating_base INT NULL,
    rating_step INT NULL,
    language NVARCHAR(100) NOT NULL,
    algorithms NVARCHAR(1000) NOT NULL,
    data_structures NVARCHAR(1000) NOT NULL,
    complexity_estimate NVARCHAR(255) NOT NULL,
    code_quality_score DECIMAL(5,2) NOT NULL,
    ai_risk_score DECIMAL(5,2) NOT NULL,
    ai_risk_level VARCHAR(30) NOT NULL,
    ds_score DECIMAL(5,2) NOT NULL,
    algorithm_score DECIMAL(5,2) NOT NULL,
    problem_solving_score DECIMAL(5,2) NOT NULL,
    consistency_score DECIMAL(5,2) NOT NULL,
    overall_score DECIMAL(5,2) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    remote_prefix NVARCHAR(30) NOT NULL,
    source_kind NVARCHAR(30) NOT NULL,
    summary NVARCHAR(MAX) NOT NULL
);

INSERT INTO @LegacyProfiles
VALUES
    (N'CODEFORCES', N'cf_ada_demo', 4, 4, 800, 60, N'GNU C++17', N'implementation, sorting, greedy', N'array, vector, string', N'O(n log n)', 79, 19, 'LOW', 68, 64, 66, 88, 74, '2026-05-01', '2026-05-31', N'CF-ADA', N'SORTING', N'Stable beginner-to-intermediate progress. Strong implementation basics.'),
    (N'CODEFORCES', N'cf_bruno_demo', 4, 3, 1000, 80, N'GNU C++17', N'bfs, greedy, prefix sums, binary search', N'array, queue, set', N'O(n log n)', 78, 32, 'MEDIUM', 76, 73, 72, 70, 75, '2026-05-01', '2026-05-31', N'CF-BRU', N'BINARY_SEARCH', N'Good BFS, prefix sum and greedy coverage. Review binary search edge cases.'),
    (N'CODEFORCES', N'cf_celine_demo', 4, 3, 1300, 100, N'Java 21', N'dsu, dijkstra, segment tree, dynamic programming', N'graph, priority queue, segment tree, array', N'O((V+E) log V)', 78, 39, 'MEDIUM', 90, 84, 82, 64, 82, '2026-05-01', '2026-05-31', N'CF-CEL', N'GRAPH_DP', N'Strong advanced data structures and graph algorithms. Some style variance should be reviewed.'),
    (N'VJUDGE', N'vj_dan_demo', 4, 4, 900, 60, N'PyPy 3', N'simulation, string processing, dijkstra, binary search', N'list, graph, priority queue, array', N'O(n log n)', 77, 27, 'LOW', 74, 71, 72, 82, 76, '2026-05-01', '2026-05-31', N'VJ-DAN', N'PREFIX_GRAPH', N'Balanced practice on simulation, string processing, graph and binary search.'),
    (N'VJUDGE', N'vj_emi_demo', 4, 3, 1100, 70, N'GNU C++17', N'fenwick tree, dynamic programming, bfs, string processing', N'fenwick tree, queue, graph, array', N'O(log n)', 80, 26, 'LOW', 83, 78, 79, 76, 80, '2026-05-01', '2026-05-31', N'VJ-EMI', N'DP_MIXED', N'Strong data structure coverage with Fenwick tree, DP and BFS examples.'),
    (N'CODEFORCES', N'demo_cf_nova', 6, 3, 800, 70, N'GNU C++17', N'sorting, brute force', N'array, vector', N'O(n log n)', 58, 12, 'LOW', 38, 42, 41, 36, 42.50, '2026-04-26', '2026-05-13', N'DEMO-CF-NOVA', N'SORTING', N'Phân loại: Trung bình. Dữ liệu giả lập phục vụ báo cáo.'),
    (N'CODEFORCES', N'demo_cf_orion', 9, 6, 1000, 90, N'Java 21', N'sorting, binary search, greedy', N'array, list, map', N'O(n log n)', 72, 28, 'LOW', 61, 66, 63, 57, 62.40, '2026-04-26', '2026-05-13', N'DEMO-CF-ORION', N'BINARY_SEARCH', N'Phân loại: Khá. Dữ liệu giả lập phục vụ báo cáo.'),
    (N'CODEFORCES', N'demo_cf_lyra', 15, 12, 1300, 110, N'GNU C++17', N'dfs, bfs, dijkstra, dynamic programming, greedy', N'vector, queue, priority queue, graph, matrix', N'O(V + E)', 86, 18, 'LOW', 84, 88, 82, 78, 82.70, '2026-04-26', '2026-05-13', N'DEMO-CF-LYRA', N'GRAPH_DP', N'Phân loại: Tốt. Dữ liệu giả lập phục vụ báo cáo.'),
    (N'VJUDGE', N'demo_vj_minh', 11, 7, 900, 60, N'PyPy 3', N'greedy, prefix sum, dfs', N'array, stack, queue, graph', N'O(n)', 68, 46, 'MEDIUM', 64, 58, 60, 62, 58.80, '2026-04-26', '2026-05-13', N'DEMO-VJ-MINH', N'PREFIX_GRAPH', N'Phân loại: Khá. Dữ liệu giả lập phục vụ báo cáo.'),
    (N'VJUDGE', N'demo_vj_sora', 18, 15, 1400, 95, N'GNU C++17', N'dynamic programming, graph traversal, binary search, greedy, sorting', N'vector, map, set, queue, priority queue, graph', N'O(n log n)', 90, 72, 'HIGH', 87, 91, 86, 80, 76.20, '2026-04-26', '2026-05-13', N'DEMO-VJ-SORA', N'DP_MIXED', N'Phân loại: Tốt. AI usage risk cần kiểm chứng thủ công; dữ liệu giả lập phục vụ báo cáo.');

;WITH Numbers AS (
    SELECT TOP (18) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS n
    FROM sys.all_objects
)
MERGE dbo.submissions AS target
USING (
    SELECT p.platform_id,
           h.handle_id,
           CONCAT(lp.remote_prefix, N'-', RIGHT(CONCAT(N'0000', CAST(n.n AS NVARCHAR(10))), 4)) AS platform_submission_id,
           CONCAT(N'DEMO-', UPPER(REPLACE(lp.handle, N'demo_', N'')), N'-', RIGHT(CONCAT(N'00', CAST(n.n AS NVARCHAR(10))), 2)) AS problem_code,
           CASE lp.source_kind
               WHEN N'SORTING' THEN N'Demo Sorting Warmup'
               WHEN N'BINARY_SEARCH' THEN N'Demo Search and Greedy'
               WHEN N'GRAPH_DP' THEN N'Demo Graph and DP Challenge'
               WHEN N'PREFIX_GRAPH' THEN N'Demo Prefix and Graph Practice'
               ELSE N'Demo Mixed Advanced Practice'
           END AS problem_name,
           CASE WHEN lp.platform_code = N'CODEFORCES' THEN N'DEMO-CF' ELSE N'DEMO-VJ' END AS contest_id,
           lp.language,
           CASE WHEN n.n <= lp.accepted_count THEN 'OK'
                WHEN n.n % 3 = 0 THEN 'TIME_LIMIT_EXCEEDED'
                ELSE 'WRONG_ANSWER'
           END AS verdict,
           DATEADD(MINUTE, n.n * 37, DATEADD(DAY, n.n - 1, CAST('2026-04-26T08:00:00' AS DATETIME2(0)))) AS submitted_at,
           40 + n.n * 17 AS execution_time_ms,
           CAST(262144 + n.n * 65536 AS BIGINT) AS memory_bytes,
           lp.rating_base + n.n * lp.rating_step AS problem_rating,
           lp.algorithms AS problem_tags,
           CASE
               WHEN lp.platform_code = N'CODEFORCES'
                   THEN CONCAT(N'https://codeforces.com/contest/demo/submission/', lp.remote_prefix, N'-', RIGHT(CONCAT(N'0000', CAST(n.n AS NVARCHAR(10))), 4))
               ELSE CONCAT(N'https://vjudge.net/solution/', lp.remote_prefix, N'-', RIGHT(CONCAT(N'0000', CAST(n.n AS NVARCHAR(10))), 4))
           END AS source_url
    FROM @LegacyProfiles lp
    JOIN dbo.platforms p ON p.code = lp.platform_code
    JOIN dbo.programming_handles h ON h.platform_id = p.platform_id AND h.handle = lp.handle
    JOIN Numbers n ON n.n <= lp.submission_count
) AS source
ON target.platform_id = source.platform_id AND target.platform_submission_id = source.platform_submission_id
WHEN NOT MATCHED THEN
    INSERT (platform_id, handle_id, platform_submission_id, problem_code, problem_name, contest_id, language, verdict,
            submitted_at, execution_time_ms, memory_bytes, problem_rating, problem_tags, source_url)
    VALUES (source.platform_id, source.handle_id, source.platform_submission_id, source.problem_code, source.problem_name,
            source.contest_id, source.language, source.verdict, source.submitted_at, source.execution_time_ms,
            source.memory_bytes, source.problem_rating, source.problem_tags, source.source_url);

INSERT INTO dbo.source_codes (submission_id, code_content, code_hash, line_count, char_count, fetched_at, storage_type, is_encrypted)
SELECT s.submission_id,
       CASE
           WHEN s.language LIKE N'%Java%' THEN N'import java.util.*; class Main { public static void main(String[] args) { System.out.println("demo"); } }'
           WHEN s.language LIKE N'%PyPy%' THEN N'from collections import deque' + CHAR(10) + N'print("demo")'
           ELSE N'#include <bits/stdc++.h>' + CHAR(10) + N'using namespace std; int main(){ vector<int> a={1,2,3}; sort(a.begin(),a.end()); cout<<a.size(); }'
       END,
       CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), s.platform_submission_id)), 2),
       2,
       LEN(s.platform_submission_id) + 80,
       SYSUTCDATETIME(),
       'DATABASE',
       0
FROM dbo.submissions s
JOIN dbo.programming_handles h ON h.handle_id = s.handle_id
JOIN @LegacyProfiles lp ON lp.handle = h.handle
WHERE s.platform_submission_id LIKE lp.remote_prefix + N'-%'
  AND NOT EXISTS (SELECT 1 FROM dbo.source_codes sc WHERE sc.submission_id = s.submission_id);

INSERT INTO dbo.ai_analysis_results (
    submission_id, analyzer_type, analyzer_version, model_name, data_structures, algorithms,
    complexity_estimate, code_quality_score, ai_risk_score, ai_risk_level, summary, raw_response, prompt_hash
)
SELECT s.submission_id,
       'HYBRID',
       'consolidated-root-sql',
       N'demo-simulated-analyzer',
       lp.data_structures,
       lp.algorithms,
       lp.complexity_estimate,
       lp.code_quality_score,
       lp.ai_risk_score,
       lp.ai_risk_level,
       lp.summary,
       N'{"demo":true,"source":"consolidated-root-sql"}',
       CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', CONVERT(VARBINARY(MAX), CONCAT(lp.handle, N'|', s.platform_submission_id, N'|consolidated-root-sql'))), 2)
FROM dbo.submissions s
JOIN dbo.programming_handles h ON h.handle_id = s.handle_id
JOIN @LegacyProfiles lp ON lp.handle = h.handle
WHERE s.platform_submission_id LIKE lp.remote_prefix + N'-%'
  AND NOT EXISTS (
      SELECT 1
      FROM dbo.ai_analysis_results ar
      WHERE ar.submission_id = s.submission_id
        AND ar.analyzer_version = 'consolidated-root-sql'
  );

MERGE dbo.user_skill_scores AS target
USING (
    SELECT h.handle_id,
           lp.period_start,
           lp.period_end,
           lp.ds_score,
           lp.algorithm_score,
           lp.problem_solving_score,
           lp.code_quality_score,
           lp.consistency_score,
           lp.ai_risk_score,
           lp.overall_score,
           lp.summary
    FROM @LegacyProfiles lp
    JOIN dbo.platforms p ON p.code = lp.platform_code
    JOIN dbo.programming_handles h ON h.platform_id = p.platform_id AND h.handle = lp.handle
) AS source
ON target.handle_id = source.handle_id AND target.period_start = source.period_start AND target.period_end = source.period_end
WHEN MATCHED THEN
    UPDATE SET data_structure_score = source.ds_score,
               algorithm_score = source.algorithm_score,
               problem_solving_score = source.problem_solving_score,
               code_quality_score = source.code_quality_score,
               practice_consistency_score = source.consistency_score,
               ai_usage_risk_score = source.ai_risk_score,
               overall_score = source.overall_score,
               summary = source.summary,
               generated_at = SYSUTCDATETIME(),
               updated_at = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (handle_id, period_start, period_end, data_structure_score, algorithm_score, problem_solving_score,
            code_quality_score, practice_consistency_score, ai_usage_risk_score, overall_score, summary)
    VALUES (source.handle_id, source.period_start, source.period_end, source.ds_score, source.algorithm_score,
            source.problem_solving_score, source.code_quality_score, source.consistency_score, source.ai_risk_score,
            source.overall_score, source.summary);
GO

IF OBJECT_ID(N'dbo.Users', N'V') IS NOT NULL
    DROP VIEW dbo.Users;
GO

CREATE VIEW dbo.Users
AS
SELECT
    h.handle_id AS user_id,
    p.code AS platform,
    p.name AS platform_name,
    h.handle,
    h.display_name,
    h.group_name,
    h.rating,
    h.rank_name,
    h.general_evaluation,
    h.consent_status,
    h.is_active,
    h.last_crawled_at,
    h.created_at,
    h.updated_at
FROM dbo.programming_handles h
JOIN dbo.platforms p ON p.platform_id = h.platform_id;
GO

IF OBJECT_ID(N'dbo.AnalysisResults', N'V') IS NOT NULL
    DROP VIEW dbo.AnalysisResults;
GO

CREATE VIEW dbo.AnalysisResults
AS
SELECT
    ar.analysis_id,
    ar.submission_id,
    ar.data_structures,
    ar.algorithms,
    CAST(ar.ai_risk_score AS INT) AS ai_generated_probability,
    ar.analyzer_type,
    ar.model_name,
    ar.summary,
    ar.raw_response,
    ar.created_at,
    ar.updated_at
FROM dbo.ai_analysis_results ar;
GO

SELECT
    (SELECT COUNT(*) FROM dbo.platforms) AS platform_count,
    (SELECT COUNT(*) FROM dbo.programming_handles) AS handle_count,
    (SELECT COUNT(*) FROM dbo.submissions) AS submission_count,
    (SELECT COUNT(*) FROM dbo.source_codes) AS source_code_count,
    (SELECT COUNT(*) FROM dbo.ai_analysis_results) AS analysis_count,
    (SELECT COUNT(*) FROM dbo.user_skill_scores) AS score_count,
    (SELECT COUNT(*) FROM dbo.crawl_logs) AS crawl_log_count;
GO
