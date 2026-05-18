# AI Analysis Specification

This document defines how source code analysis should behave.

## Goals

The analyzer should identify:

- Programming language.
- Data structures.
- Algorithms.
- Time complexity.
- Space complexity.
- Problem-solving level.
- Code quality.
- Algorithm score.
- Data-structure score.
- AI-assistance risk signals.
- Warnings and confidence.

## Analyzer Modes

| Mode | Purpose |
|---|---|
| Gemini REST | Primary real AI analysis. |
| OpenAI-compatible REST | Alternative provider integration. |
| Rule-based | Offline deterministic analysis. |
| Mock mode | Stable demo and UI testing. |

## Required JSON Fields

The AI provider must return valid JSON matching the application schema:

```json
{
  "language": "cpp",
  "algorithms": ["dynamic_programming"],
  "data_structures": ["array"],
  "complexity_time": "O(n)",
  "complexity_space": "O(n)",
  "problem_solving_level": "intermediate",
  "code_quality_score": 80,
  "algorithm_score": 85,
  "ds_score": 75,
  "ai_generated_probability": 25,
  "ai_usage_evidence": ["no_clear_evidence"],
  "explanation_vi": "Giai thich ngan gon bang tieng Viet.",
  "warnings": [],
  "confidence": 80
}
```

## Score Rules

All numeric scores must be between `0` and `100`.

Interpretation:

- `0-39`: weak or high-risk area.
- `40-69`: acceptable but needs improvement.
- `70-84`: strong.
- `85-100`: excellent.

AI-generated probability:

- `0-29`: low signal.
- `30-59`: moderate signal.
- `60-100`: high signal requiring manual review.

This probability is not proof.

## Risk Evidence

Allowed evidence values include:

- `overly_generic_variable_names`
- `excessive_comments`
- `template_like_code`
- `unusual_consistency`
- `unused_code`
- `mixed_style`
- `very_polished_explanation_style`
- `suspicious_structure_for_level`
- `no_clear_evidence`

## Prompt Safety

The system prompt must require:

- Valid JSON only.
- No invented facts.
- Lower confidence when context is limited.
- Neutral Vietnamese explanation.
- Non-accusatory AI-risk language.

## Rate Limit Behavior

When an AI provider returns quota or rate-limit errors:

- Stop the current batch.
- Mark the job as `QUOTA_DELAYED`.
- Store a sanitized error.
- Avoid repeated immediate retries.

## Rule-Based Fallback

Rule-based analysis is used when:

- `ai.provider=rule-based`
- mock mode is enabled
- real provider is unavailable for demo purposes

Rule-based output should be deterministic and safe for tests.

## Human Review Requirement

AI-risk output must be reviewed with context:

- Problem statement.
- Submission timing.
- User history.
- Code evolution.
- Platform rules.

The application must not make final misconduct decisions.
