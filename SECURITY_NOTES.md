# Security Notes

AI Code Analyzer Desktop handles online judge data, source code, local credentials, and optional AI provider keys. This document defines the security boundaries that must be preserved.

## Security Principles

- Store secrets outside Git.
- Crawl only data the user is allowed to view.
- Treat source code as sensitive user data.
- Record unavailable source states instead of bypassing platform controls.
- Keep AI-risk output neutral and review-oriented.
- Prefer least privilege for database accounts.

## Secrets

Never commit:

- `src/main/resources/application.properties`
- `.env`
- API keys.
- Database passwords.
- Cookies.
- Browser profile data.
- Captured tokens.
- Exported reports containing private data unless intentionally shared.

Use environment variables:

```powershell
$env:DB_PASSWORD="..."
$env:GEMINI_API_KEY="..."
```

The example config may contain placeholders only.

## Database Security

Recommended database account:

- Use an application-specific SQL login.
- Grant access only to `CodeAnalyzerDb`.
- Avoid using `sa`.
- Avoid storing plaintext production credentials in config files.

The local project uses `trustServerCertificate=true` for development convenience. For production-like environments, configure proper TLS certificates.

## Crawler Boundaries

Allowed:

- Public submission metadata.
- Source pages visible to the currently signed-in Chrome bot account.
- Manual user login.
- Manual captcha solving by the user.

Not allowed:

- Captcha bypass.
- Cloudflare bypass.
- Hidden source bypass.
- Private contest extraction without permission.
- Cookie scraping for reuse outside the session.
- Storing session tokens.

If a page is not accessible, the app must save an explicit unavailable state and reason.

## AI Analysis Safety

The analyzer may estimate AI-assistance probability, but this is not proof.

Required behavior:

- Use phrases such as "risk signal", "needs review", and "confidence".
- Avoid declaring that a user cheated.
- Reduce confidence when source is short, template-heavy, or missing problem context.
- Preserve raw provider errors only after sanitization.

## Logging

Logs must not expose:

- Passwords.
- API keys.
- Tokens.
- Cookies.
- Authorization headers.
- Full sensitive provider response bodies.

Sanitization should mask keys matching patterns such as:

```text
password=
token=
api_key=
api-key=
```

## Reports

Generated reports can include:

- Handles.
- Submission metadata.
- Source-derived findings.
- Skill scores.
- AI-risk indicators.

Treat reports as private unless the user explicitly intends to share them.

## Public Demo Rules

Before public demonstration:

- Use demo handles.
- Use a demo browser profile.
- Hide API keys and passwords.
- Do not show private contest source code.
- Prefer mock mode if network or quota reliability is uncertain.

## Security Checklist

- `git status` shows no local secret files staged.
- `application.properties` is ignored.
- Environment variables are used for secrets.
- Chrome bot uses an isolated profile.
- SQL account is not `sa`.
- Reports do not contain unintended private data.
- AI-risk wording remains non-final and non-accusatory.
