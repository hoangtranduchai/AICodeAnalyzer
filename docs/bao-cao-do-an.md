# BÁO CÁO ĐỒ ÁN

## Đề tài: AI Code Analyzer Desktop

**Ứng dụng JavaFX quản lý nick lập trình, thu thập submission, phân tích source code và đánh giá năng lực thuật toán**

---

## 1. Mở đầu

Trong quá trình học tập các môn lập trình, cấu trúc dữ liệu, giải thuật và lập trình thi đấu, sinh viên thường sử dụng các nền tảng trực tuyến như Codeforces, VJudge, CSES, SPOJ hoặc các hệ thống online judge khác để luyện tập. Các nền tảng này ghi nhận lịch sử nộp bài, ngôn ngữ lập trình, trạng thái bài nộp và trong một số trường hợp có thể truy cập source code nếu người dùng có quyền hợp lệ.

Tuy nhiên, việc đánh giá năng lực của một người học dựa trên dữ liệu luyện tập vẫn còn mang tính thủ công. Giảng viên hoặc người hướng dẫn thường phải xem từng bài nộp, đọc source code, nhận diện thuật toán, cấu trúc dữ liệu, chất lượng triển khai và tiến độ luyện tập. Công việc này tốn thời gian, đặc biệt khi cần theo dõi nhiều nick lập trình trong một lớp hoặc nhóm học thuật.

Đề tài **AI Code Analyzer Desktop** được xây dựng nhằm hỗ trợ quá trình quản lý nick lập trình, lưu trữ submission, phân tích source code bằng phương pháp rule-based và AI, từ đó tổng hợp thành điểm năng lực và báo cáo đánh giá. Ứng dụng được phát triển theo hướng Java Desktop với giao diện JavaFX, sử dụng SQL Server làm cơ sở dữ liệu, có scheduler chạy định kỳ và hỗ trợ xuất báo cáo.

## 2. Lý do chọn đề tài

Đề tài được lựa chọn vì có tính thực tiễn cao trong môi trường đào tạo lập trình. Việc học thuật toán hiện nay không chỉ dựa trên bài kiểm tra trên lớp mà còn dựa nhiều vào quá trình luyện tập trên các nền tảng online judge. Nếu khai thác hợp lý dữ liệu công khai hoặc dữ liệu do người dùng cung cấp hợp lệ, hệ thống có thể giúp giảng viên, trợ giảng hoặc nhóm học thuật có thêm góc nhìn định lượng về năng lực của từng người học.

Bên cạnh đó, sự phát triển của các mô hình AI hỗ trợ lập trình đặt ra nhu cầu đánh giá code một cách thận trọng hơn. Hệ thống không nhằm kết luận chắc chắn source code có do AI tạo ra hay không, mà chỉ đưa ra các dấu hiệu tham khảo như độ đồng đều bất thường của comment, style code, sự thay đổi năng lực đột ngột hoặc tần suất giải bài khó trong thời gian ngắn. Đây là hướng tiếp cận phù hợp hơn về mặt đạo đức và giáo dục.

Đề tài cũng phù hợp để sinh viên thực hành nhiều mảng kiến thức quan trọng: lập trình Java Desktop, JDBC, SQL Server, thiết kế kiến trúc nhiều tầng, xử lý JSON, lập lịch bằng `ScheduledExecutorService`, crawler trực tiếp web, kiểm thử tự động, bảo mật cấu hình và xuất báo cáo.

## 3. Mục tiêu đề tài

Mục tiêu chính của đề tài là xây dựng một ứng dụng desktop có khả năng quản lý nick lập trình, thu thập dữ liệu bài nộp, phân tích source code và sinh báo cáo đánh giá năng lực.

Các mục tiêu cụ thể gồm:

- Xây dựng giao diện desktop bằng JavaFX, có sidebar, dashboard, bảng dữ liệu, form nhập liệu và biểu đồ.
- Kết nối SQL Server bằng JDBC, không hard-code thông tin đăng nhập trong source code.
- Quản lý platform như Codeforces, VJudge và danh sách handle/nick tương ứng.
- Thu thập metadata submission từ Codeforces thông qua API công khai.
- Hỗ trợ crawl trực tiếp VJudge qua web với Chrome debug session.
- Lưu submission, source code, log crawl, kết quả phân tích và điểm năng lực vào cơ sở dữ liệu.
- Phân tích source code bằng AI REST API nếu có API key, đồng thời có fallback rule-based khi không có API key.
- Đánh giá năng lực từng handle qua các nhóm điểm: cấu trúc dữ liệu, thuật toán, giải quyết vấn đề, chất lượng code, độ ổn định và rủi ro sử dụng AI.
- Xuất báo cáo PDF/Excel phục vụ demo, tổng kết hoặc báo cáo học tập.
- Có tài liệu README, INSTALLATION, USER_GUIDE, test plan và release checklist.

## 4. Phạm vi đề tài

Phạm vi đề tài tập trung vào ứng dụng desktop chạy trên Windows hoặc môi trường có JDK/Maven, sử dụng SQL Server làm nơi lưu trữ dữ liệu.

Các chức năng nằm trong phạm vi:

- Quản lý platform và handle.
- Crawl metadata Codeforces qua API `user.status`.
- Crawl dữ liệu VJudge trực tiếp qua web.
- Lưu source code nếu dữ liệu nguồn có cung cấp source code.
- Đánh dấu `SOURCE_NOT_AVAILABLE` khi không có source code hợp lệ.
- Phân tích source code bằng rule-based analyzer và Google Gemini REST analyzer.
- Tính điểm năng lực dựa trên submission, kết quả phân tích và lịch sử luyện tập.
- Dashboard thống kê tổng quan.
- Xuất báo cáo PDF và Excel.
- Kiểm thử unit test bằng JUnit 5.

Các nội dung ngoài phạm vi hoặc chưa triển khai hoàn chỉnh:

- Không bypass đăng nhập, không crawl dữ liệu riêng tư trái phép.
- Không cam kết phát hiện chính xác code do AI tạo ra.
- Không thay thế hoàn toàn đánh giá chuyên môn của giảng viên.
- Chưa triển khai hệ thống phân quyền nhiều người dùng.
- Chưa đóng gói installer Windows hoàn chỉnh.
- Chưa kiểm thử tích hợp trực tiếp với SQL Server thật trong môi trường bàn giao nếu máy chưa cấu hình database.

## 5. Cơ sở lý thuyết

### 5.1. JavaFX và ứng dụng desktop

JavaFX là thư viện xây dựng giao diện đồ họa cho Java, hỗ trợ các thành phần như `TableView`, `Chart`, `Form`, `Button`, `TextArea`, `ComboBox`, layout pane và CSS. Trong đề tài, JavaFX được sử dụng để xây dựng giao diện desktop hiện đại, có khả năng hiển thị dashboard, bảng danh sách, biểu đồ, màn hình chi tiết source code và màn hình cấu hình scheduler.

### 5.2. JDBC và SQL Server

JDBC là API chuẩn của Java để kết nối và thao tác với cơ sở dữ liệu quan hệ. Ứng dụng sử dụng Microsoft SQL Server JDBC Driver để kết nối SQL Server. Các repository dùng `PreparedStatement` nhằm truyền tham số an toàn, giảm rủi ro SQL Injection và tách câu lệnh SQL khỏi dữ liệu đầu vào.

SQL Server được sử dụng để lưu trữ các bảng chính như `platforms`, `programming_handles`, `submissions`, `source_codes`, `ai_analysis_results`, `user_skill_scores`, `crawl_logs`, `app_settings` và `error_logs`.

### 5.3. Layered Architecture

Layered Architecture chia hệ thống thành nhiều tầng có trách nhiệm riêng:

- UI hiển thị và nhận thao tác người dùng.
- Controller xử lý sự kiện giao diện.
- Service chứa nghiệp vụ.
- Repository/DAO truy xuất dữ liệu.
- Model/Entity biểu diễn dữ liệu.
- Module phụ trợ gồm Scheduler, Crawler, Analyzer và Report.

Cách tổ chức này giúp code dễ bảo trì, dễ kiểm thử và hạn chế phụ thuộc chéo giữa giao diện, nghiệp vụ và cơ sở dữ liệu.

### 5.4. Crawler trực tiếp web và API

Crawler trong đề tài được hiểu là thành phần thu thập dữ liệu từ nguồn bên ngoài một cách có kiểm soát. Với Codeforces, hệ thống sử dụng API công khai để lấy metadata submission và đọc source trực tiếp từ trang submission. Với VJudge, hệ thống crawl trực tiếp qua trang `status` và `solution` bằng Chrome debug session.

Các nguyên tắc quan trọng:

- Chỉ lấy dữ liệu công khai hoặc dữ liệu người dùng có quyền truy cập.
- Không spam request.
- Có rate limit và retry giới hạn.
- Ghi log lỗi để theo dõi.
- Không cố lấy source code nếu trang web không cho xem.

### 5.5. Phân tích source code bằng AI và rule-based

Hệ thống hỗ trợ hai hướng phân tích:

- **AI Analyzer**: gửi source code đến Google Gemini REST API bằng `java.net.http.HttpClient`, yêu cầu trả về JSON gồm thuật toán, cấu trúc dữ liệu, độ phức tạp, chất lượng code và xác suất rủi ro dùng AI.
- **RuleBasedCodeAnalyzer**: dùng regex/heuristic để phát hiện các dấu hiệu như `vector`, `map`, `set`, `queue`, `stack`, `priority_queue`, sorting, binary search, DFS/BFS, dynamic programming, graph và greedy.

Rule-based analyzer đóng vai trò fallback khi không có API key, giúp ứng dụng vẫn hoạt động được trong môi trường demo hoặc offline.

### 5.6. Scheduler

`ScheduledExecutorService` một luồng được sử dụng để lập lịch crawl tự động hằng ngày theo chu kỳ 24 giờ. Người dùng có thể cấu hình giờ chạy và bật/tắt auto crawl. Trước khi workflow định kỳ chạy, Java tự mở Chrome headless với `--remote-debugging-port=9222` và `--user-data-dir="C:\CF_Bot_Profile"` nếu CDP chưa sẵn sàng, nhờ đó tận dụng lại phiên đăng nhập của profile bot. Workflow được bảo vệ bằng một `AtomicBoolean` để không chạy chồng hai chu kỳ.

### 5.7. Kiểm thử phần mềm

Đề tài sử dụng JUnit 5 cho unit test. Các nhóm test chính gồm:

- Analyzer test.
- Crawler test với mock HTTP.
- Repository test với H2 ở chế độ tương thích SQL Server.
- Service test cho scoring, feedback, report và Gemini mock response.
- Parser test cho VJudge import (legacy).

## 6. Phân tích yêu cầu

### 6.1. Yêu cầu chức năng

| Mã | Yêu cầu | Mô tả |
|---|---|---|
| FR-01 | Quản lý platform | Lưu Codeforces, VJudge và thông tin API/base URL |
| FR-02 | Quản lý handle | Thêm, sửa, xóa, làm mới danh sách nick |
| FR-03 | Chống trùng handle | Không cho trùng `platform + handle` |
| FR-04 | Crawl Codeforces | Lấy metadata submission từ API công khai |
| FR-05 | Crawl VJudge | Crawl trực tiếp web VJudge qua Chrome debug session |
| FR-06 | Lưu submission | Lưu metadata bài nộp, verdict, language, rating, tags |
| FR-07 | Lưu source code | Lưu code nếu nguồn hợp lệ có cung cấp |
| FR-08 | Chống trùng submission | Không trùng theo `platform + remote_submission_id` |
| FR-09 | Phân tích AI | Phân tích thuật toán, cấu trúc dữ liệu, độ phức tạp, chất lượng code |
| FR-10 | Fallback heuristic | Phân tích rule-based khi không có API key |
| FR-11 | Tính điểm năng lực | Tính DS, Algorithm, Problem Solving, Quality, Consistency, AI Risk, Overall |
| FR-12 | Dashboard | Hiển thị tổng quan handle, submission, analysis, lỗi crawl |
| FR-13 | Scheduler | Crawl tự động hằng ngày và crawl thủ công |
| FR-14 | Báo cáo | Xuất PDF/Excel |
| FR-15 | Log lỗi | Ghi log crawl và thông báo lỗi thân thiện |

### 6.2. Yêu cầu phi chức năng

| Nhóm | Yêu cầu |
|---|---|
| Bảo mật | Không hard-code API key/password; dùng biến môi trường hoặc file cấu hình ngoài source |
| Tin cậy | Có xử lý lỗi database, crawler, AI API và report |
| Hiệu năng | Crawler có rate limit, không gửi request dồn dập |
| Bảo trì | Code chia package theo layered architecture |
| Mở rộng | Có interface `OnlineJudgeCrawler` và `CodeAnalyzer` để thêm platform/analyzer mới |
| Kiểm thử | Có unit test cho repository, service, analyzer, crawler và report |
| Đạo đức | Không kết luận chắc chắn code do AI viết; chỉ đưa ra dấu hiệu cần kiểm chứng |
| Tương thích | Chạy với JDK 21, Maven, SQL Server 2019+ hoặc SQL Server Express |

## 7. Thiết kế hệ thống

### 7.1. Kiến trúc tổng thể

Ứng dụng được thiết kế theo layered architecture kết hợp các module chức năng chuyên biệt.

```text
┌────────────────────────────────────────────────────────────┐
│                         JavaFX UI                           │
│ Workspace | AI Review | Reports | Settings & Guide          │
└───────────────────────────┬────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────┐
│                       UI Controller                         │
│ DashboardController | HandleController                      │
│ SourceCodeDetailController | SchedulerSettingsController    │
└───────────────────────────┬────────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────────┐
│                         Service                            │
│ HandleAccountService | CrawlService | AnalysisService       │
│ SkillScoringService | ReportService | ExcelReportService    │
└───────────────┬───────────────┬───────────────┬────────────┘
                │               │               │
┌───────────────▼───┐   ┌───────▼───────┐   ┌──▼────────────┐
│ Repository / DAO  │   │ Crawler       │   │ AI Analyzer   │
│ JDBC + SQL Server │   │ CF/VJudge     │   │ Gemini/Rules  │
└───────────────┬───┘   └───────┬───────┘   └──┬────────────┘
                │               │              │
┌───────────────▼───────────────▼──────────────▼────────────┐
│                         SQL Server                         │
│ platforms, handles, submissions, source_codes, analyses    │
│ skill_scores, crawl_logs, app_settings, error_logs         │
└────────────────────────────────────────────────────────────┘
```

### 7.2. Các package chính

| Package | Trách nhiệm |
|---|---|
| `app` | Entry point JavaFX, khởi tạo màn hình chính |
| `config` | Đọc cấu hình database, AI, crawler, scheduler |
| `model` | Các class dữ liệu ánh xạ với bảng SQL |
| `repository` | CRUD và truy vấn bằng JDBC |
| `service` | Xử lý nghiệp vụ, transaction, scoring, report |
| `crawler` | Interface crawler, Codeforces crawler, VJudge crawler trực tiếp web |
| `importer` | Parser/import dữ liệu VJudge (legacy, không dùng trong luồng direct web) |
| `analyzer` | Rule-based analyzer, prompt builder, Gemini analyzer |
| `scheduler` | ScheduledExecutorService, job crawl tự động |
| `ui.controller` | Controller cho các màn hình JavaFX |
| `report` | Xây dựng dữ liệu báo cáo, export PDF/Excel |
| `util` | Tiện ích JSON, date/time, validation, secret masking |
| `exception` | Exception nghiệp vụ: database, crawler, analyzer, report |

### 7.3. Luồng dữ liệu chính

#### Luồng thêm handle và crawl dữ liệu

```text
Người dùng nhập platform + handle trong Workspace
        ↓
HandleController validate dữ liệu
        ↓
HandleAccountService kiểm tra trùng
        ↓
HandleAccountRepository lưu SQL Server
        ↓
Người dùng bấm Crawl & Analyze Now, bấm Crawl từng nick hoặc Scheduler tự chạy
        ↓
CrawlService chọn crawler theo platform
        ↓
CodeforcesCrawler gọi API và đọc trang submission / VJudge đọc trang solution
        ↓
SubmissionUpsertService chống trùng submission
        ↓
Lưu submissions, source_codes, crawl_logs
```

#### Luồng phân tích source code

```text
Người dùng chọn source code
        ↓
SourceCodeDetailController hiển thị metadata và code
        ↓
Người dùng bấm Send AI Analysis
        ↓
AnalysisService gọi analyzer
        ↓
GeminiAnalyzerService hoặc RuleBasedCodeAnalyzer
        ↓
AiAnalysisResultRepository lưu kết quả
        ↓
UI hiển thị thuật toán, cấu trúc dữ liệu, điểm và nhận xét
```

#### Luồng tính điểm và xuất báo cáo

```text
Submissions + AI Analysis Results
        ↓
SkillScoringService tính điểm thành phần và overall
        ↓
SkillScoreRepository lưu user_skill_scores
        ↓
ReportDataBuilder gom dữ liệu
        ↓
PdfReportExporter / ExcelReportService xuất file
        ↓
File lưu trong thư mục reports
```

## 8. Thiết kế cơ sở dữ liệu

### 8.1. Danh sách bảng

| Bảng | Mục đích |
|---|---|
| `platforms` | Lưu nền tảng online judge |
| `programming_handles` | Lưu nick/handle theo platform |
| `submissions` | Lưu metadata bài nộp |
| `source_codes` | Lưu source code hoặc trạng thái không có source |
| `ai_analysis_results` | Lưu kết quả phân tích AI/rule-based |
| `user_skill_scores` | Lưu điểm năng lực tổng hợp theo handle |
| `crawl_logs` | Lưu log các lần crawl |
| `app_settings` | Lưu cấu hình ứng dụng như scheduler |
| `error_logs` | Lưu lỗi đã sanitize |

### 8.2. Quan hệ dữ liệu

```text
platforms 1 ─── n programming_handles
platforms 1 ─── n submissions
programming_handles 1 ─── n submissions
submissions 1 ─── 0..1 source_codes
submissions 1 ─── n ai_analysis_results
programming_handles 1 ─── n user_skill_scores
```

### 8.3. Ràng buộc chính

- `platforms.code` là duy nhất.
- `programming_handles(platform_id, handle)` là duy nhất.
- `submissions(platform_id, platform_submission_id)` là duy nhất.
- `source_codes.submission_id` là duy nhất để mỗi submission có một bản source code hiện hành.
- Các điểm trong `ai_analysis_results` và `user_skill_scores` có CHECK từ 0 đến 100.
- Các bảng chính có `created_at`, `updated_at`.

### 8.4. Chỉ mục

Các index chính trong script SQL:

- `IX_programming_handles_platform`
- `IX_submissions_handle_id`
- `IX_submissions_platform_id`
- `IX_submissions_platform_submission_id`
- `IX_source_codes_submission_id`
- `IX_ai_analysis_results_submission_id`
- `IX_user_skill_scores_handle_id`
- `IX_crawl_logs_started_at`

Các chỉ mục này hỗ trợ truy vấn dashboard, tìm submission theo handle, chống trùng submission và lấy kết quả phân tích theo submission.

### 8.5. Bảo mật dữ liệu

Các thông tin cần bảo vệ:

- Mật khẩu SQL Server.
- Gemini API key.
- File `application.properties` thật.
- Source code nếu thuộc dữ liệu riêng tư.
- Raw response từ AI nếu chứa nội dung nhạy cảm.

Ứng dụng không commit file cấu hình thật. `.gitignore` loại trừ `application.properties`, `.env`, logs, reports và file database local. API key và password được khuyến nghị lưu bằng biến môi trường.

## 9. Xây dựng chương trình

### 9.1. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 |
| Build tool | Maven |
| UI Desktop | JavaFX |
| Database | SQL Server |
| Database access | JDBC |
| JSON | Jackson |
| Scheduler | ScheduledExecutorService |
| HTTP client | Java HttpClient |
| HTML parser/crawl helper | Jsoup |
| PDF report | OpenPDF |
| Excel report | Apache POI |
| Logging | SLF4J, Logback, Log4j-to-SLF4J bridge |
| Unit test | JUnit 5 |
| Test database | H2 compatibility mode |

### 9.2. Các màn hình chính

| Màn hình | Chức năng |
|---|---|
| Workspace | Thêm/sửa/xóa nick, mở Chrome bot, crawl từng nick hoặc crawl & phân tích toàn bộ, xem tổng quan |
| AI Review | Xem source code, copy code, gửi phân tích AI/rule-based và xem kết quả |
| Reports | Xem bảng điểm năng lực, Top N AI risk/overall, chọn thời gian, định dạng và xuất báo cáo |
| Settings & Guide | Cấu hình AI, SQL Server, lịch crawl, theme/ngôn ngữ và hướng dẫn sử dụng |

### 9.3. Chống trùng submission

Hệ thống dùng khóa duy nhất theo `platform_id + platform_submission_id`. Khi crawl, nếu submission đã tồn tại:

- Nếu metadata không đổi thì bỏ qua.
- Nếu verdict, language hoặc metadata thay đổi thì cập nhật.
- Nếu source code thay đổi thì cập nhật source code.
- Kết quả trả về thống kê `new_count`, `updated_count`, `skipped_count`, `failed_count`.

### 9.4. AI analyzer và fallback

Khi có API key, `GeminiAnalyzerService` gửi source code đến Gemini REST endpoint, yêu cầu kết quả dạng JSON thuần. Khi không có API key hoặc bật mock mode, hệ thống dùng rule-based analyzer để phát hiện cấu trúc dữ liệu, thuật toán và tính điểm sơ bộ.

Phần đánh giá AI usage risk được trình bày cẩn trọng bằng các cụm như “có dấu hiệu cần kiểm chứng thêm”, không dùng ngôn ngữ quy kết.

### 9.5. Báo cáo

Hệ thống hỗ trợ xuất báo cáo:

- PDF bằng OpenPDF.
- Excel bằng Apache POI.

File báo cáo được lưu trong thư mục `reports`. Báo cáo gồm thông tin handle, submission, kết quả phân tích, điểm năng lực và nhận xét.

## 10. Kiểm thử

### 10.1. Chiến lược kiểm thử

Đề tài sử dụng kiểm thử tự động bằng JUnit 5, tập trung vào các thành phần có nghiệp vụ rõ ràng:

- `RuleBasedCodeAnalyzer`
- `AIDetectionHeuristics`
- `CodeforcesCrawler`
- Luồng smoke test thủ công qua UI: `Workspace`, `AI Review`, `Operations`, `Reports`
- `HandleAccountRepository`
- `SubmissionRepository`
- `GeminiAnalyzerService`
- `SkillScoringService`
- `SkillFeedbackGenerator`
- `ReportService`
- `ExcelReportService`

Các test crawler và Gemini không gọi API thật. Codeforces crawler dùng mock HTTP response. Gemini analyzer dùng mock response và mock mode.

### 10.2. Kết quả chạy test

Lệnh kiểm thử:

```powershell
mvn test
```

Kết quả kiểm tra lại ngày 17/05/2026:

| Chỉ số | Kết quả |
|---|---:|
| Tổng số test | 86 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Build | SUCCESS |

Các nhóm test đã chạy:

| Nhóm test | Số test | Kết quả |
|---|---:|---|
| Analyzer | 6 | Pass |
| App/Config | 3 | Pass |
| Crawler | 4 | Pass |
| VJudge crawler | 3 | Pass |
| Repository | 11 | Pass |
| Report service | 2 | Pass |
| Gemini analyzer service | 5 | Pass |
| Skill feedback/scoring | 13 | Pass |

Lưu ý: khi chạy trong môi trường sandbox không có quyền mạng, Maven có thể không tải được plugin từ Maven Central. Sau khi cho Maven truy cập mạng để resolve dependency, toàn bộ test chạy thành công. Đây là vấn đề môi trường build, không phải lỗi compile/test của source code.

## 11. Kết quả thử nghiệm

### 11.1. Dữ liệu demo

Dự án có script `sql/ai-code-analyzer-complete.sql` tạo schema và dữ liệu demo gồm:

- 2 platform: Codeforces và VJudge.
- 5 handle demo.
- 20 submission demo.
- 20 source code demo bằng C++/Java/Python.
- 20 kết quả phân tích AI giả lập.
- 5 bản ghi điểm đánh giá năng lực.

Dữ liệu demo được ghi rõ là dữ liệu giả lập, không khẳng định là dữ liệu thật.

### 11.2. Kết quả chức năng

| Chức năng | Kết quả |
|---|---|
| Build Maven | Thành công |
| Unit test | 86/86 pass |
| Kết nối cấu hình DB | Có `DatabaseConfig`, `DatabaseConnectionFactory`, xử lý lỗi thân thiện |
| Quản lý handle | Có form, validate trùng, bảng danh sách |
| Codeforces crawler | Có API call, parse JSON, rate limit, retry, mock test |
| VJudge crawler | Crawl trực tiếp web, yêu cầu đăng nhập hợp lệ |
| AI analyzer | Có Gemini REST service, mock mode, retry, timeout |
| Rule-based analyzer | Phát hiện DS/algorithm cơ bản |
| Skill scoring | Có công thức và test nhiều tình huống |
| Report | Xuất PDF/Excel, có test tạo file |
| Bảo mật secret | Không hard-code API key/password thật |

### 11.3. Một số kết quả quan sát

- Dashboard có thể hiển thị số lượng handle, submission, source đã phân tích và lỗi crawl gần đây.
- Các bảng quản lý dữ liệu không crash khi dữ liệu rỗng.
- Khi không có source code hợp lệ, hệ thống lưu metadata và đánh dấu `SOURCE_NOT_AVAILABLE`.
- Gemini analyzer có thể chạy ở mock mode khi không có API key.
- Report Excel có 5 sheet: tổng quan, handles, submissions, AI analysis và skill scores.

## 12. Đánh giá ưu điểm/hạn chế

### 12.1. Ưu điểm

- Kiến trúc nhiều tầng rõ ràng, dễ mở rộng và bảo trì.
- Sử dụng JavaFX phù hợp với yêu cầu Java Desktop có giao diện đồ họa.
- Repository dùng JDBC thuần và `PreparedStatement`, phù hợp yêu cầu học thuật.
- Có cơ chế chống trùng submission theo platform và remote submission id.
- Có rate limit và retry cho crawler.
- Không crawler cưỡng bức VJudge, phù hợp hơn về pháp lý và đạo đức.
- Có fallback rule-based khi không có API key.
- Có test tự động cho nhiều module quan trọng.
- Có tài liệu cài đặt, hướng dẫn sử dụng, release checklist và test plan.
- Hỗ trợ xuất báo cáo PDF/Excel phục vụ đồ án.

### 12.2. Hạn chế

- Chưa có hệ thống đăng nhập và phân quyền người dùng.
- Chưa có installer Windows hoàn chỉnh.
- Chưa kiểm thử tự động toàn bộ UI JavaFX bằng công cụ UI testing.
- Chưa kiểm thử tích hợp SQL Server thật trong mọi môi trường triển khai.
- Việc đánh giá AI usage risk chỉ mang tính xác suất, phụ thuộc dữ liệu lịch sử và đặc điểm code.
- Codeforces API công khai không cung cấp source code, nên nhiều trường hợp chỉ lưu được metadata.
- Một số controller còn có thể tiếp tục tách nhỏ để giảm trách nhiệm của `MainApp`.
- Chưa có cơ chế mã hóa source code trong database ở mức production.

### 12.3. Báo cáo thử nghiệm

Bảng dưới đây tổng hợp kết quả đánh giá từ dữ liệu demo/thu nghiệm (không phải kết luận về người thật):

| Handle | Nền tảng | CTDL | Thuật toán | Chất lượng | Rủi ro AI | Tổng | Nhận xét |
|---|---|---:|---:|---:|---:|---:|---|
| demo_cf_alpha | Codeforces | 68 | 72 | 70 | 18 | 69 | Ổn định, dùng cấu trúc dữ liệu cơ bản |
| demo_cf_beta | Codeforces | 74 | 76 | 73 | 22 | 73 | Có dấu hiệu dùng greedy/graph ở mức trung bình |
| demo_cf_gamma | Codeforces | 60 | 64 | 66 | 28 | 63 | Cần bổ sung thuật toán nâng cao |
| demo_vj_delta | VJudge | 55 | 58 | 61 | 30 | 58 | Cần cải thiện chất lượng code và độ ổn định |
| demo_vj_epsilon | VJudge | 70 | 69 | 72 | 20 | 70 | Có tiến bộ, ít dấu hiệu bất thường |

File báo cáo PDF demo nằm trong thư mục `reports/` và có thể xuất lại từ màn hình **Danh giá & Báo cáo**.

## 13. Hướng phát triển

Các hướng phát triển tiếp theo:

- Đóng gói ứng dụng thành installer Windows bằng `jpackage`.
- Bổ sung đăng nhập, phân quyền người dùng và cấu hình theo vai trò.
- Tách đầy đủ các màn hình còn lại thành controller riêng thay vì để nhiều logic trong `MainApp`.
- Bổ sung UI test tự động cho các luồng chính.
- Tích hợp thêm platform khác như CSES, AtCoder hoặc SPOJ nếu điều khoản sử dụng cho phép.
- Bổ sung mã hóa source code hoặc raw AI response trong database.
- Cải thiện công thức scoring bằng dữ liệu lịch sử dài hạn.
- Tạo biểu đồ tiến bộ theo thời gian.
- Cho phép cấu hình model AI, prompt và schema phân tích trong giao diện.
- Bổ sung chức năng so sánh style code giữa các giai đoạn luyện tập.
- Xuất báo cáo DOCX hoặc HTML.

## 14. Kết luận

Đề tài **AI Code Analyzer Desktop** đã xây dựng được một ứng dụng Java Desktop phục vụ quản lý nick lập trình, thu thập dữ liệu submission, lưu source code, phân tích bằng AI/rule-based, tính điểm năng lực và xuất báo cáo. Dự án đáp ứng các yêu cầu chính về giao diện JavaFX, kết nối SQL Server, crawler trực tiếp web, scheduler, AI analyzer, dashboard, scoring, report và tài liệu hướng dẫn.

Về mặt kỹ thuật, hệ thống áp dụng layered architecture, dùng JDBC thuần với `PreparedStatement`, có cơ chế chống trùng dữ liệu, có fallback khi thiếu API key và có kiểm thử tự động. Về mặt đạo đức, hệ thống không cố truy cập dữ liệu riêng tư và không kết luận chắc chắn việc sử dụng AI, mà chỉ đưa ra dấu hiệu cần kiểm chứng thêm.

Kết quả test hiện tại đạt 86/86 test pass. Điều này cho thấy các thành phần nghiệp vụ quan trọng đã được kiểm chứng ở mức unit test. Dự án đã build được file jar thực thi đầy đủ dependency; các hướng phát triển thêm như UI test tự động, installer Windows, phân quyền và mã hóa dữ liệu nâng cao vẫn có thể tiếp tục bổ sung sau.

## 15. Tài liệu tham khảo

1. Oracle/OpenJDK Documentation, Java Platform Standard Edition.
2. OpenJFX Documentation, JavaFX Controls and UI Components.
3. Apache Maven Documentation, Project Build and Dependency Management.
4. Microsoft Documentation, SQL Server and Microsoft JDBC Driver for SQL Server.
5. Oracle Java Documentation, ScheduledExecutorService and concurrency utilities.
6. FasterXML Jackson Documentation, JSON Processing for Java.
7. Apache POI Documentation, Microsoft Office Document Export.
8. OpenPDF Documentation, PDF Generation for Java.
9. JUnit 5 User Guide, Unit Testing Framework for Java.
10. Codeforces API Documentation, Public User Status API.
11. Google Gemini API Documentation, REST API and Structured JSON Responses.
12. OWASP Secure Coding Practices, input validation, secret management and logging safety.
