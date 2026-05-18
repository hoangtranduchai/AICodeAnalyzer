# Privacy And Data Retention

This project stores programming handles, submission metadata, source code, analysis results, and generated reports. Treat this data as private unless the user explicitly shares it.

## Data Stored

The application may store:

- Platform names.
- Programming handles.
- Submission metadata.
- Source code content.
- Source crawl status and errors.
- AI and rule-based analysis results.
- Skill scores.
- Crawl logs.
- Generated reports.

## Data Not Intended To Be Stored

The application must not store:

- Browser cookies.
- Session tokens.
- API keys.
- Database passwords.
- Captcha solution data.
- Personal browser profile data.

## Retention

Local SQL Server data remains until deleted by the user.

Generated reports remain in:

```text
reports/
```

Delete reports manually when they are no longer needed.

## Deletion

Deleting a handle should cascade related submissions, source code, analysis results, and skill scores through database relationships where configured.

For a full reset, recreate `CodeAnalyzerDb` from the SQL script.

## Sharing

Before sharing reports or screenshots:

- Remove real API keys.
- Hide private handles if needed.
- Avoid showing private contest source.
- Confirm AI-risk wording is not presented as a final accusation.

## Demo Recommendation

Use demo accounts, demo handles, seeded data, and mock mode for public presentations.
