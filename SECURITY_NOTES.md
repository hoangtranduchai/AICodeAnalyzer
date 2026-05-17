# Security Notes

## 2026-05-14 High-priority fixes

- Prevented `src/main/resources/application.properties` from being packaged into the Maven build artifact. This avoids accidentally shipping a local file after a developer adds a real DB password or API key.
- Changed configuration loading to prefer external local configuration:
  - JVM system property: `-Dapp.config=/path/to/application.properties`
  - Environment variable: `APP_CONFIG_FILE=/path/to/application.properties`
  - Working-directory file: `application.properties`
  - Safe classpath fallback: `application.properties.example`
- Added `.env.example` with placeholder-only values and updated `.gitignore` so the example can be tracked while real `.env` files remain ignored.

## 2026-05-14 Production hardening pass

- Added centralized runtime message sanitization for common secret patterns before notifications/log-facing import errors display sensitive values.
- Changed SQL setup documentation to grant an explicit application role on required tables instead of broad `db_datareader` and `db_datawriter` database roles.
- Added import guardrails for user-provided VJudge files: regular-file validation, 10 MB file size limit, and 10,000 row limit.
- Reduced source-code exposure during browsing by loading Source Detail metadata first and fetching `code_content` only after a source record is selected.
- Added `IX_submissions_submitted_recent` to support recent-submission screens without relying on a handle-prefixed index.

## 2026-05-16 Authorized source crawling

- Added Codeforces/VJudge source fetching without bypassing login, CAPTCHA, Cloudflare, private contests, or hidden source settings.
- Codeforces source fetch order is now: signed official API when `CODEFORCES_API_KEY`/`CODEFORCES_API_SECRET` are configured, then authorized Chrome DevTools session, then public HTTP page fallback.
- VJudge source fetch order is now: user-provided import/ZIP, authorized Chrome DevTools source page, then Gemini OCR for authorized snapshot images when `GEMINI_API_KEY` is configured.
- API keys and browser cookies are not stored in the database. The app reads Codeforces/Gemini secrets from environment variables or local ignored config only.
- Source records now preserve provenance in `source_codes.storage_type` where possible, for example `CODEFORCES_API`, `CODEFORCES_AUTHORIZED_HTML`, `VJUDGE_ZIP_ARCHIVE`, or `VJUDGE_AUTHORIZED_SNAPSHOT_OCR`.
- Failed source attempts use explicit non-source states such as `LOGIN_REQUIRED`, `PERMISSION_DENIED`, `CAPTCHA_REQUIRED`, `OCR_REQUIRED`, or `OCR_FAILED` instead of pretending source was available.

## Not changed in this pass

- The application is still a local JavaFX desktop app without an application-level login or admin/user role model. Adding real auth/role enforcement would be a behavior and architecture change, so it was not included in this minimal high-priority fix.
- Existing databases that already used broad SQL roles must be migrated manually by removing `db_datareader`/`db_datawriter` membership and applying the explicit grants from the installation guide.
- At-rest encryption for stored source code is still recommended before handling private or regulated source data.
