# Reporting Specification

The reporting system turns stored crawl and analysis data into reviewable PDF and Excel artifacts.

## Report Goals

Reports should answer:

- Which handles were evaluated?
- What submissions were analyzed?
- Which algorithms and data structures appear?
- What are the skill scores?
- What warnings or AI-risk signals require manual review?
- What evidence supports the summary?

## Output Directory

```text
reports/
```

## PDF Report

Purpose:

- Human-readable evaluation.
- Suitable for demonstration or handoff.

Expected content:

- Report title and generation time.
- Handle identity.
- Platform.
- Submission summary.
- Skill scores.
- Analysis narrative.
- AI-risk disclaimer.
- Warnings and limitations.

## Excel Report

Purpose:

- Structured review.
- Filtering, sorting, and external analysis.

Expected sheets may include:

- Handle summary.
- Submission rows.
- Analysis results.
- Skill score snapshots.
- Crawl status summary.

Expected row fields:

- Platform.
- Handle.
- Problem code.
- Problem name.
- Language.
- Verdict.
- Submitted time.
- Source status.
- Algorithms.
- Data structures.
- Complexity.
- Code quality score.
- AI-risk score.
- Summary.

## Score Display

Scores must remain in the range `0-100`.

Suggested labels:

- `0-39`: Needs attention.
- `40-69`: Developing.
- `70-84`: Strong.
- `85-100`: Excellent.

AI-risk score labels should be phrased as review levels, not accusations.

## Data Preconditions

Before generating reports:

- Handles exist.
- Submissions are present.
- Source code or source unavailable state is recorded.
- Analysis results exist for meaningful evaluation.
- Skill scores are calculated.

## Privacy

Reports may contain source-derived findings and user handles. Review generated files before sharing.
