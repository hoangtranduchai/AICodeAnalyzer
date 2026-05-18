# Crawler Compliance

AI Code Analyzer Desktop must crawl responsibly and within platform access boundaries.

## Core Rule

The application may only fetch source code that the current user or bot account is allowed to view in the browser.

## Allowed Behavior

- Fetch public Codeforces metadata.
- Fetch public VJudge metadata.
- Use a signed-in Chrome bot session for pages the account can view.
- Let the user manually sign in.
- Let the user manually solve captcha or challenges.
- Record source unavailable states.
- Respect rate limits and retry limits.

## Forbidden Behavior

- Bypassing captcha.
- Bypassing Cloudflare.
- Accessing hidden contest source without permission.
- Extracting private source through unauthorized APIs.
- Hard-coding cookies or tokens.
- Storing browser session tokens.
- Sharing private source without consent.

## Codeforces Flow

1. Fetch metadata through public API when possible.
2. For source code, open the submission page through authorized browser session.
3. Parse visible source from HTML.
4. If source is hidden or inaccessible, save unavailable state.

## VJudge Flow

1. Fetch status data.
2. Open solution page through authorized browser session.
3. Parse visible source when available.
4. If source is an authorized image snapshot, OCR may be used with Gemini.
5. If login, captcha, permission, or hidden contest blocks access, save unavailable state.

## Source States

Crawler logic should map platform outcomes into explicit states:

- `AVAILABLE`
- `SOURCE_NOT_AVAILABLE`
- `LOGIN_REQUIRED`
- `PERMISSION_DENIED`
- `CONTEST_HIDDEN`
- `RATE_LIMITED`
- `CAPTCHA_REQUIRED`
- `OCR_REQUIRED`
- `OCR_FAILED`

## Audit Expectations

The application should record:

- Platform.
- Handle.
- Remote submission id.
- Source crawl status.
- Source origin.
- Sanitized failure reason.
- Crawl log summary.

The application should not record:

- Cookies.
- Tokens.
- Authorization headers.
- Raw browser profile data.

## Demo Guidance

Use demo accounts and demo handles. Do not show private contest source or personal browser sessions in public.
