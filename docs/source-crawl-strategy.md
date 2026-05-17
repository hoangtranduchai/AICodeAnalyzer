# Source Crawl Strategy

## Mục tiêu

Hệ thống chỉ crawl trực tiếp trên web Codeforces và VJudge để lấy source code phân tích. Không bypass captcha, không vượt quyền truy cập, không crawl dữ liệu private trái phép.

## Nguyên tắc bắt buộc

- Chỉ crawl trực tiếp từ trang web Codeforces/VJudge.
- Không nhập file thủ công (CSV/JSON/HTML/ZIP).
- Không bypass Cloudflare/captcha; người dùng tự đăng nhập và tự giải challenge.
- Không hard-code cookie/token/API key vào source.
- Tôn trọng rate limit, retry có giới hạn.

## Codeforces

Luồng chuẩn:

1. Lấy danh sách submission theo handle từ endpoint công khai (không cần cookie).
2. Người dùng mở Chrome debug và đăng nhập Codeforces.
3. Ứng dụng truy cập trực tiếp trang submission, parse HTML để lấy source code.
4. Nếu trang không cho xem: lưu metadata và gắn `SOURCE_NOT_AVAILABLE`/`LOGIN_REQUIRED`/`PERMISSION_DENIED`.

## VJudge

Luồng chuẩn:

1. Lấy danh sách submission theo handle từ `https://vjudge.net/status/data`.
2. Dùng Chrome debug session truy cập `https://vjudge.net/solution/{runId}` để lấy source.
3. Chỉ lấy source khi submission bật Share code và contest không ẩn source.
4. Nếu trang trả snapshot ảnh, dùng Gemini OCR khi có `GEMINI_API_KEY` và người dùng có quyền xem ảnh.

## Pipeline đề xuất

1. Metadata crawl:
   - Codeforces `user.status`.
   - VJudge `status/data`.
2. Source acquisition:
   - Direct HTML source qua Chrome debug session.
   - OCR snapshot ảnh VJudge khi được phép.
3. Normalization:
   - Chuẩn hóa `platform_submission_id`, `problem_code`, `language`, `verdict`.
   - Tính `code_hash`, `line_count`, `char_count`.
4. Analysis:
   - Rule-based trước để có kết quả ổn định.
   - Gemini/API sau để diễn giải sâu hơn.
5. Audit:
   - Log nguồn lấy code: authorized HTML hoặc OCR.
   - Không log cookie, token, API key.

## Trạng thái hiện tại

- App hỗ trợ source fetch trực tiếp qua Chrome DevTools session tại `http://localhost:9222`.
- Codeforces: metadata lấy từ endpoint công khai; source chỉ lấy qua trang submission.
- VJudge: source lấy từ `/solution/{runId}`; nếu snapshot ảnh thì OCR khi có Gemini key.
- Trạng thái chi tiết: `AVAILABLE`, `SOURCE_NOT_AVAILABLE`, `LOGIN_REQUIRED`, `PERMISSION_DENIED`, `CONTEST_HIDDEN`, `RATE_LIMITED`, `CAPTCHA_REQUIRED`, `OCR_REQUIRED`, `OCR_FAILED`.
- Source origin: `CODEFORCES_AUTHORIZED_HTML`, `VJUDGE_AUTHORIZED_HTML`, `VJUDGE_AUTHORIZED_SNAPSHOT_OCR`.
- Khi thiếu Chrome session, thiếu quyền, hoặc trang đổi format, app vẫn lưu metadata và ghi trạng thái phù hợp.

## Cách chạy crawl trực tiếp

1. Mở Chrome debug:

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222 --user-data-dir="$env:TEMP\cf-vj-debug-profile"
```

2. Trong cửa sổ Chrome vừa mở:
   - Đăng nhập Codeforces/VJudge.
   - Tự giải captcha/challenge nếu trang yêu cầu.

3. Kiểm tra port:

```text
http://localhost:9222/json/version
```

4. Chạy app:

```powershell
mvn javafx:run
```

5. Nhấn **Crawl & Analyze Now** trong **Workspace** để crawl trực tiếp toàn bộ submission mới, hoặc bấm **Crawl** trên từng dòng nick để crawl riêng một handle.
