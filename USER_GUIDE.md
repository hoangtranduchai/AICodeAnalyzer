# USER GUIDE - AI Code Analyzer Desktop

Tài liệu này dành cho người dùng cuối khi vận hành app.

## 1. Mở ứng dụng

Chạy bằng Maven:

```powershell
mvn javafx:run
```

Hoặc chạy jar đã build:

```powershell
java -jar target/ai-code-analyzer-desktop-1.0.0-SNAPSHOT-all.jar
```

Trước khi mở app, đảm bảo SQL Server đang chạy và `DB_PASSWORD` đã được set.

## 1.1. Chuyển ngôn ngữ

Ứng dụng hỗ trợ 2 ngôn ngữ: tiếng Việt và tiếng Anh.

- Bấm nút `VI/EN` ở cuối sidebar bên trái để đổi ngôn ngữ ngay lập tức.
- Khi đổi ngôn ngữ, màn hình hiện tại sẽ tự dựng lại với nhãn, nút, bảng và thông báo tương ứng.
- Có thể đặt ngôn ngữ mặc định khi chạy app:

```powershell
mvn javafx:run "-Dapp.language=vi"
mvn javafx:run "-Dapp.language=en"
```

## 2. Chuẩn bị Chrome bot

Mở màn hình `Workspace`, bấm:

```text
Initialize Browser Bot
```

Ứng dụng sẽ mở Chrome thật với profile riêng:

```text
C:\CF_Bot_Profile
```

Trong Chrome này:

1. Đăng nhập Codeforces bằng tài khoản phụ có rating.
2. Đăng nhập VJudge nếu cần crawl VJudge.
3. Tự xử lý captcha/challenge nếu có.
4. Có thể thu nhỏ Chrome sau khi đăng nhập.

Không dùng nick chính để crawl tự động.

Nếu nút mở Chrome không hoạt động trên máy hiện tại, trong `Workspace` có khu vực `Lệnh khôi phục Chrome`. Copy lệnh `Chrome hiện` và chạy thủ công trong PowerShell.

## 3. Quản lý nick

Mở `Workspace`, dùng khối `Thêm nick nhanh`.

Các thao tác:

- Chọn nền tảng `Codeforces` hoặc `VJudge`.
- Nhập handle.
- Thêm, cập nhật, xóa hoặc làm mới danh sách.

Hệ thống không cho trùng `platform + handle`.

## 4. Crawl trực tiếp

Mở `Workspace`.

1. Đảm bảo Chrome bot báo `Chrome CDP sẵn sàng`.
2. Bấm `Crawl & phân tích ngay` để crawl toàn bộ nick và phân tích source sau đó.
3. Hoặc bấm nút `Crawl` trên từng dòng nick để crawl riêng nick đó.
4. Theo dõi toast, tổng quan vận hành và dữ liệu trong `AI Review` / `Reports`.

Hệ thống crawl toàn bộ submission mới, không áp trần cố định theo số lượng. Submission đã có trong DB sẽ được bỏ qua trước khi mở trang source.

## 5. Crawl tự động hằng ngày

Mở `Settings`.

1. Chọn `Daily run time`.
2. Bật `Daily auto crawl all new code` hoặc nhãn tiếng Việt tương ứng.
3. Bấm `Save Schedule` / `Lưu lịch`.

Job `ScheduledExecutorService` chạy 1 ngày 1 lần, lấy nick active trong DB, tự mở Chrome headless CDP bằng profile bot nếu cần, crawl code mới, phân tích Gemini/rule-based và lưu kết quả.

## 6. Phân tích source code

Mở `AI Review`.

1. Chọn source code đã crawl.
2. Bấm `Phân tích AI` / `Analyze AI`.
3. Chờ kết quả.

Nếu có `GEMINI_API_KEY`, app gọi Gemini REST API. Nếu không có key hoặc bật mock mode, app dùng rule-based fallback.

Kết quả gồm:

- Cấu trúc dữ liệu.
- Thuật toán.
- Điểm chất lượng code.
- Rủi ro AI.
- Nhận xét.

Rủi ro AI chỉ là dấu hiệu tham khảo, không phải kết luận gian lận.

## 7. Tổng quan vận hành

Mở `Workspace`.

Màn hình hiển thị:

- Tổng số nick.
- Tổng submission.
- Source đã phân tích.
- Lỗi crawl gần đây.
- Chart submission theo nền tảng.
- Chart điểm thuật toán.
- Top nick theo tổng điểm.
- Top nick có rủi ro AI cao.

Bấm `Làm mới` / `Refresh` để tải lại dữ liệu từ SQL Server.

## 8. Báo cáo

Mở `Reports`.

1. Chọn khoảng ngày.
2. Chọn định dạng `PDF` hoặc `Excel`.
3. Chọn có mở file sau khi xuất hay không.
4. Bấm `Xuất báo cáo` / `Export report`.

File được tạo trong:

```text
reports/
```

## 9. Cấu hình Gemini API

Set API key trong PowerShell:

```powershell
$env:GEMINI_API_KEY="your_gemini_api_key"
```

Set lâu dài:

```powershell
setx GEMINI_API_KEY "your_gemini_api_key"
```

Sau `setx`, mở PowerShell mới rồi chạy app.

## 10. Lỗi thường gặp

### Không mở được Chrome bot

- Kiểm tra Chrome đã cài.
- Kiểm tra `chrome.exe` có trong PATH.
- Mở thủ công lệnh hiển thị trong `Workspace` -> `Lệnh khôi phục Chrome`.

### Không crawl được source

- Chrome debug chưa mở.
- Chưa đăng nhập tài khoản phụ.
- Submission không có quyền xem source.
- Website đang yêu cầu captcha/challenge.
- VJudge đang hiển thị source dạng ảnh nhưng chưa có `GEMINI_API_KEY` để OCR.

### Không kết nối được DB

- Kiểm tra SQL Server service.
- Kiểm tra `application.properties`.
- Kiểm tra `DB_PASSWORD`.
- Kiểm tra đã chạy `sql/ai-code-analyzer-complete.sql`.

### AI không trả kết quả

- Kiểm tra internet.
- Kiểm tra `GEMINI_API_KEY`.
- Kiểm tra `ai.mock-mode`.
- Nếu thiếu key, app vẫn có thể chạy rule-based fallback.

## 11. Nguyên tắc sử dụng

- Chỉ crawl dữ liệu bạn có quyền truy cập.
- Không dùng tài khoản chính cho bot.
- Không chia sẻ API key, DB password, cookie hoặc Chrome profile.
- Không dùng điểm rủi ro AI như kết luận kỷ luật tự động.
