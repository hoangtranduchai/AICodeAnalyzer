# Test Plan - JavaFX AI Code Analyzer Desktop

## 1. Thông tin chung

| Mục | Nội dung |
|---|---|
| Dự án | AI Code Analyzer Desktop |
| Công nghệ | Java 21, JavaFX, SQL Server, JDBC, Playwright CDP, ScheduledExecutorService, Jackson, Gemini REST API, OpenPDF, Apache POI |
| Phạm vi | Kiểm thử chức năng nhập nick, crawl trực tiếp web, phân tích AI/rule-based, chấm điểm, scheduler, báo cáo PDF/Excel |
| CSDL test | `CodeAnalyzerDb` với `sql/ai-code-analyzer-complete.sql` |
| Trạng thái mặc định | `Planned` = chưa chạy, `Passed` = đạt, `Failed` = lỗi, `Blocked` = bị chặn |

## 2. Unit Test

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| UT-001 | Kiểm tra `DatabaseConfig` đọc cấu hình hợp lệ | Properties có `db.url`, `db.username`, `db.password` | Gọi `DatabaseConfig.fromProperties()` | Trả về config đúng giá trị, timeout mặc định hợp lệ | Passed |
| UT-002 | Kiểm tra validate config DB thiếu trường bắt buộc | Properties thiếu `db.url` | Gọi `DatabaseConfig.fromProperties()` | Ném `DatabaseException` với thông báo thân thiện | Passed |
| UT-006 | Kiểm tra `RuleBasedCodeAnalyzer` phát hiện DS/algorithm | Source có `vector`, `map`, `queue`, `dfs`, `sort` | Gọi `analyze()` | Kết quả có DS/algorithm tương ứng, analyzer type `RULE_BASED` | Passed |
| UT-007 | Kiểm tra `AIDetectionHeuristics` không quy kết | Source và lịch sử có dấu hiệu rủi ro | Gọi `evaluate()` | Trả xác suất/evidence/warnings, không kết luận chắc chắn | Passed |
| UT-008 | Kiểm tra `SkillScoringService` với dữ liệu ít | 2 submissions, 1 analysis | Gọi `calculateScore()` | Có cảnh báo dữ liệu ít, overall bị giảm hợp lý | Passed |
| UT-009 | Kiểm tra `SkillFeedbackGenerator` 5 mức điểm | SkillScore ở các mức Yếu đến Rất tốt | Gọi `generate()` | Sinh đúng mẫu nhận xét theo mức điểm | Passed |
| UT-010 | Kiểm tra analyzer mock/fallback mode | Không có API key hoặc `ai.mock-mode=true` | Gọi `analyze()` | Không gọi HTTP thật, trả result mock hợp lệ | Passed |

## 3. Integration Test

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| IT-001 | Kiểm tra kết nối SQL Server thật | `application.properties` trỏ tới SQL Server local | Chạy chức năng test connection | Kết nối thành công, hiển thị thông báo thành công | Planned |
| IT-002 | Kiểm tra lưu handle qua service/repository | Platform `CODEFORCES`, handle `demo_cf_test` | Gọi `HandleAccountService.addHandle()` | Bản ghi xuất hiện trong `programming_handles`, không trùng platform + handle | Planned |
| IT-003 | Kiểm tra transaction lưu submission + source | Submission mới có source code | Gọi service lưu transaction | Submission và source cùng được lưu; nếu source lỗi thì rollback | Planned |
| IT-004 | Kiểm tra upsert chống trùng submission | Crawl 2 lần cùng `platform + remote_submission_id` | Gọi `BackendWorkflowService.runOnce()` 2 lần với crawler giả | Lần 1 lưu submission/source/analysis; lần 2 không tạo trùng analysis | Passed |
| IT-005 | Kiểm tra phân tích source và lưu DB | Source code hợp lệ trong DB | Mở `AI Review`, chọn source và bấm Analyze/Re-analyze hoặc chạy workflow `Crawl & Analyze Now` | Có bản ghi mới trong `ai_analysis_results` với analyzer Gemini hoặc rule-based theo cấu hình | Passed |
| IT-006 | Kiểm tra chấm điểm handle và lưu DB | Handle có submissions + analyses | Gọi workflow phân tích hoặc `SkillScoringService.calculateAndSave(handleId)` | Có bản ghi trong `user_skill_scores`, điểm nằm trong 0-100 | Passed |
| IT-007 | Kiểm tra build dữ liệu báo cáo | Khoảng ngày `2026-04-26` đến `2026-05-13`, 5 handle demo | Gọi `ReportDataBuilder.build()` | Trả đủ danh sách handle, thống kê, scores, feedback | Planned |

## 4. UI Test thủ công

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| UI-001 | Kiểm tra mở ứng dụng JavaFX | `mvn javafx:run` | Khởi động app | Màn hình `Workspace` hiển thị đúng, không crash | Planned |
| UI-002 | Kiểm tra hướng dẫn Chrome bot | - | Quan sát card workflow trong `Workspace` | Có trạng thái Chrome CDP, nút mở/kiểm tra Chrome, lệnh copy Chrome hiện/headless | Planned |
| UI-003 | Kiểm tra crawl trực tiếp | Nick demo | Nhập nick trong `Workspace`, bấm `Crawl & Analyze Now` hoặc `Crawl` từng dòng | CSDL cập nhật, trạng thái rõ ràng, không cần sang màn hình khác | Planned |
| UI-004 | Kiểm tra màn hình phân tích | Source dài trên 500 dòng | Mở `AI Review` | TextArea scroll được, gauge/badge hiển thị, không treo UI, nút Copy hoạt động | Planned |
| UI-005 | Kiểm tra gửi phân tích AI | Source code hợp lệ | Bấm Phan tich AI | Có loading, có kết quả hoặc lỗi thân thiện nếu thiếu API key | Planned |
| UI-006 | Kiểm tra màn hình lịch crawl | Auto crawl on/off, giờ chạy `01:00` | Lưu cấu hình, refresh status | Trạng thái scheduler và lần chạy gần nhất hiển thị đúng | Planned |
| UI-007 | Kiểm tra báo cáo | Chọn ngày và handle demo | Bấm export PDF/Excel | File tạo trong `reports`, UI hiển thị thông báo đường dẫn | Planned |
| UI-008 | Kiểm tra song ngữ UI | App đang mở ở bất kỳ màn hình chính | Bấm nút `VI/EN` ở cuối sidebar | Sidebar, nút, bảng, thông báo và màn hình hiện tại đổi giữa tiếng Việt/tiếng Anh | Passed |
| UI-009 | Kiểm tra sidebar readiness/responsive | Thiếu/sai DB hoặc AI config, đổi kích thước mobile/tablet/desktop | Khởi động app, quan sát cuối sidebar và resize cửa sổ | Pill DB/AI hiển thị checking/ready/mock/missing/error rõ ràng; mobile ẩn badge phụ để không tràn | Passed |
| UI-010 | Kiểm tra Dashboard polish | Dark/light mode, resize desktop/tablet/mobile, hover chart, bấm Refresh, đổi số dòng Top N, chọn dòng bảng xếp hạng | Toast ở góc phải dưới gọn hơn; metric card hiển thị 4/2/1 cột theo desktop/tablet/mobile; không có scroll ngang ở màn hình chính; Refresh/loading không làm nhảy layout; hover cột chart hiện tooltip nhanh; bảng Top N full-width, wrap text, có tooltip và panel chi tiết khi chọn dòng | Passed |

## 5. Database Test

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| DB-001 | Kiểm tra tạo schema | `sql/ai-code-analyzer-complete.sql` | Chạy script trên SSMS | Tạo đủ bảng, PK, FK, constraints, indexes | Planned |
| DB-002 | Kiểm tra seed data | `sql/ai-code-analyzer-complete.sql` | Chạy script trên SSMS | Có platforms, handles, submissions, sources, analyses, scores mẫu | Planned |
| DB-003 | Kiểm tra demo report data | `sql/ai-code-analyzer-complete.sql` | Chạy script trên SSMS | Có 5 nick demo, dữ liệu giả lập không bị trùng khi chạy lại | Planned |
| DB-004 | Kiểm tra unique platform + handle | Insert trùng platform/handle | Chạy insert trùng | SQL Server chặn bằng `UQ_programming_handles_platform_handle` | Planned |
| DB-005 | Kiểm tra unique submission | Insert trùng `platform_id + platform_submission_id` | Chạy insert trùng | SQL Server chặn bằng `UQ_submissions_platform_remote` | Planned |
| DB-006 | Kiểm tra score range | Insert score `overall_score=120` | Chạy insert | SQL Server chặn bằng CHECK constraint | Planned |
| DB-007 | Kiểm tra cascade delete source/analysis | Xóa submission test | Query source/analysis theo submission | Source và analysis liên quan bị xóa theo FK cascade | Planned |
| DB-008 | Kiểm tra index cơ bản | Query theo `handle_id`, `submission_id`, `platform` | Xem execution plan hoặc query timing | Query dùng index phù hợp, không scan toàn bộ khi dữ liệu lớn | Planned |

## 6. Crawler Test

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| CR-001 | Kiểm tra Codeforces parse metadata | Mock response `user.status` hợp lệ | Gọi `CodeforcesCrawler.crawl()` | Parse đúng submission metadata, source fetch qua HTML nếu có quyền | Passed |
| CR-002 | Kiểm tra retry HTTP tạm thời | Mock HTTP `500`, `429`, sau đó `200` | Gọi crawler | Retry đúng số lần và trả kết quả thành công | Passed |
| CR-003 | Kiểm tra handle không tồn tại | Mock response Codeforces status failed | Gọi crawler | Trả lỗi thân thiện, ghi crawl log failed | Planned |
| CR-004 | Kiểm tra rate limit | Cấu hình delay > 0 | Crawl nhiều handle liên tiếp | Có delay giữa request, không spam API | Planned |
| CR-006 | Kiểm tra log lỗi từng handle | 1 handle lỗi, 1 handle thành công | Chạy crawl all | Job không dừng toàn bộ, log từng handle đúng | Planned |

## 7. Scheduler Test

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| SCH-001 | Kiểm tra start scheduler | `scheduler.auto-crawl-enabled=false` | Khởi động app | `ScheduledExecutorService` start, không tự chạy khi tắt auto | Planned |
| SCH-002 | Kiểm tra bật auto crawl | `scheduler.daily-run-time=01:00` | Gọi `SchedulerManager.configureDailyCrawl(01:00, true)` | Executor lên lịch workflow crawl code mới 1 lần/ngày, chu kỳ 24 giờ | Passed |
| SCH-003 | Kiểm tra tắt auto crawl | Auto crawl hằng ngày đang bật | Tắt và lưu | Daily trigger bị unschedule | Planned |
| SCH-004 | Kiểm tra crawl thủ công | Bấm `Crawl & Analyze Now` trong `Workspace` | Quan sát log/status | Job chạy thủ công với `jobType=MANUAL`, lưu crawl/source/analysis | Planned |
| SCH-005 | Kiểm tra không chạy song song | Trigger job 2 lần nhanh | Quan sát log scheduler | Không có 2 workflow chạy đồng thời nhờ executor một luồng và `AtomicBoolean` guard | Passed |
| SCH-008 | Kiểm tra Chrome headless CDP khi chạy lịch | Chrome debug chưa mở, profile bot đã đăng nhập trước đó | Để auto crawl trigger | Java tự gọi `cmd /c start chrome.exe --headless=new --disable-gpu --remote-debugging-port=9222 --user-data-dir=\"C:\\CF_Bot_Profile\"` trước workflow | Passed |
| SCH-006 | Kiểm tra lỗi từng handle | Một handle crawler lỗi | Chạy scheduled job | Job hoàn tất partial, handle còn lại vẫn xử lý | Planned |
| SCH-007 | Kiểm tra backend workflow đủ 5 bước | Crawler giả trả 1 submission mới có source | Gọi `BackendWorkflowService.runOnce()` | Lấy handle, crawl submission mới, lưu source, analyze, lưu `ai_analysis_results`, cập nhật `user_skill_scores` | Passed |
| SCH-009 | Kiểm tra dừng batch khi AI hết quota | Analyzer giả ném rate limit 429 ở source đầu tiên | Gọi `BackendWorkflowService.runOnce()` với 2 source pending | Workflow không spam source tiếp theo; log 1 dòng thân thiện, pending còn lại giữ để retry sau | Passed |

## 8. AI Analyzer Test

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| AI-001 | Kiểm tra Gemini request JSON schema | Mock HTTP client | Gọi `GeminiAnalyzerService.analyze()` | Request có `responseMimeType=application/json`, `responseSchema`, không log API key | Passed |
| AI-002 | Kiểm tra parse response Gemini | Mock response chứa JSON 3 key | Gọi analyze | Map đúng `data_structures`, `algorithms`, `ai_generated_probability` | Passed |
| AI-003 | Kiểm tra retry lỗi mạng Gemini | Mock IOException rồi success | Gọi analyze | Retry và trả result thành công | Passed |
| AI-004 | Kiểm tra fallback rule-based khi thiếu API key | Không set `GEMINI_API_KEY` | Gọi `AnalysisService` | Dùng `RuleBasedCodeAnalyzer`, không gọi API thật | Planned |
| AI-005 | Kiểm tra source quá ngắn | Source `int main(){}` | Gọi analyzer | Confidence thấp, có warning source quá ngắn | Passed |
| AI-006 | Kiểm tra AI usage risk language | Source có dấu hiệu risk cao | Sinh nhận xét | Chỉ ghi “có dấu hiệu cần kiểm chứng thêm”, không quy kết | Passed |
| AI-007 | Kiểm tra JSON invalid từ AI | Mock response không đúng schema | Gọi `GeminiAnalyzerService.analyze()` | Ném `AnalyzerException`, không lưu DB | Passed |
| AI-008 | Kiểm tra Gemini quota 429 | Mock Gemini trả `RESOURCE_EXHAUSTED` và `Please retry in ...` | Gọi `GeminiAnalyzerService.analyze()` | Ném `AiRateLimitException`, đọc retry delay, không lộ API key | Passed |

## 9. Report Export Test

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| RP-001 | Kiểm tra xuất PDF | `EvaluationReportData` demo | Gọi `ReportService.exportPdf()` | File `.pdf` được tạo, size > 1KB, có thông báo | Passed |
| RP-002 | Kiểm tra PDF tiếng Việt Unicode | Dữ liệu có dấu tiếng Việt | Mở PDF bằng viewer | Text tiếng Việt hiển thị đúng font, không lỗi ô vuông | Planned |
| RP-003 | Kiểm tra biểu đồ trong PDF | Overall scores khác nhau | Xuất PDF | Có bảng/biểu đồ thanh so sánh Overall Score | Passed |
| RP-004 | Kiểm tra export Excel | `ExcelReportData` demo | Gọi `ExcelReportService.exportExcel()` | File `.xlsx` được tạo, mở lại bằng POI thành công | Passed |
| RP-005 | Kiểm tra Excel đủ 5 sheet | Workbook export | Mở workbook | Có `Tong quan`, `Danh sach handles`, `Submissions`, `AI Analysis`, `Skill Scores` | Passed |
| RP-006 | Kiểm tra Excel header style/autosize | Workbook export | Mở workbook hoặc kiểm POI | Header được format, cột auto-size | Passed |
| RP-007 | Kiểm tra lọc thời gian report | Period không chứa submission | Export report | Submissions/analysis/scores lọc đúng, không crash khi rỗng | Planned |
| RP-008 | Kiểm tra mở file sau export | `openAfterExport=true` | Export PDF/Excel trong desktop | Nếu hệ điều hành hỗ trợ, file được mở; nếu không, có message thân thiện | Planned |

## 10. Security Test

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| SEC-001 | Không hard-code DB password | Source code toàn repo | Search `password`, `DB_PASSWORD` | Không có password thật trong source; chỉ có placeholder/env var | Planned |
| SEC-002 | Không log Gemini API key | API key giả `gemini-test-key` | Chạy analyzer/mock log | Log không chứa API key hoặc token đầy đủ | Passed |
| SEC-003 | Kiểm tra SQL injection handle | Handle input `abc'; DROP TABLE...` | Thêm/tìm handle | Không thực thi SQL ngoài ý muốn do dùng `PreparedStatement` | Planned |
| SEC-004 | Kiểm tra Chrome debug profile | Chrome debug mở bằng profile riêng | Crawl trực tiếp | App chỉ dùng session người dùng, không lưu cookie/token | Planned |
| SEC-005 | Kiểm tra quyền crawl | VJudge source riêng tư không có quyền | Crawl trực tiếp | App không bypass login; lưu metadata và trạng thái phù hợp | Planned |
| SEC-006 | Kiểm tra dữ liệu nhạy cảm trong report | Report PDF/Excel | Mở file report | Không chứa API key, DB password, token, cookie | Planned |
| SEC-007 | Kiểm tra cảnh báo đạo đức AI risk | Score AI risk cao | Xuất report/feedback | Chỉ ghi “có dấu hiệu cần kiểm chứng thêm”, không dùng ngôn ngữ quy kết | Passed |

## 11. Acceptance Test

| ID | Mục tiêu | Dữ liệu đầu vào | Các bước test | Kết quả mong đợi | Trạng thái |
|---|---|---|---|---|---|
| AT-001 | Cài đặt và khởi động ứng dụng | Java 21, Maven, SQL Server local | Chạy schema, cấu hình properties, `mvn javafx:run` | App mở thành công, kết nối DB được | Planned |
| AT-002 | Nhập 5 nick demo | `sql/ai-code-analyzer-complete.sql` | Nhập nick tại `Workspace` | Danh sách nick và crawl log hiển thị đúng | Planned |
| AT-003 | Crawl trực tiếp dữ liệu | Codeforces/VJudge demo | Chạy crawl trực tiếp | Submission metadata/source được lưu, log thành công/thất bại rõ ràng | Planned |
| AT-004 | Phân tích source code | Source code demo đã crawl trong DB | Mở `AI Review`, chọn source và bấm Analyze/Re-analyze | Có analysis result gồm algorithms, DS, AI generated probability và lưu DB | Passed |
| AT-005 | Tính điểm năng lực | 5 nick demo | Chạy `SkillScoringService` hoặc dùng data demo | Có DS/Algorithm/Problem/Quality/Consistency/AI Risk/Overall 0-100 | Planned |
| AT-006 | Xem bảng đánh giá | DB có demo data | Mở `Reports` | Hiển thị bảng điểm, preview và biểu đồ | Planned |
| AT-007 | Scheduler chạy tự động | Auto crawl enabled, giờ chạy gần hiện tại | Lưu cấu hình, chờ trigger | Job chạy đúng giờ, không chạy song song, có crawl log | Planned |
| AT-008 | Xuất báo cáo PDF | Period `2026-04-26` đến `2026-05-13`, 5 nick demo | Export PDF | File PDF trong `reports`, có bảng điểm, nhận xét, cảnh báo giới hạn | Planned |
| AT-009 | Xuất báo cáo Excel | Cùng dữ liệu demo | Export Excel | File Excel có đủ 5 sheet, header format, ngày xuất báo cáo | Planned |
| AT-010 | Đạt yêu cầu đồ án | Toàn bộ chức năng chính | Demo end-to-end trước hội đồng/người dùng | Luồng nhập nick/crawl/phân tích/chấm điểm/xuất báo cáo chạy được, không có lỗi nghiêm trọng | Planned |
| AT-011 | Build jar thực thi | Maven package | Chạy `mvn -q -DskipTests package`, kiểm tra `target/*-all.jar` | Tạo jar có `DesktopLauncher`, FXML và CSS | Passed |

## 12. Tiêu chí hoàn thành kiểm thử

- Tất cả unit test hiện có chạy `BUILD SUCCESS`.
- Không có lỗi blocker ở luồng chính: cấu hình DB, nhập nick, crawl trực tiếp, phân tích, chấm điểm, export báo cáo.
- Các test security liên quan API key/password đạt.
- Report PDF/Excel tạo được và mở được trên máy demo.
- Các cảnh báo về crawl dữ liệu và AI usage risk xuất hiện đúng văn phong trung lập.
