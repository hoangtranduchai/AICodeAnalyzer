# TEST_PLAN - AI Code Analyzer Desktop

## 1. Mục tiêu và phạm vi

Tài liệu này dùng để kiểm thử thủ công và bán tự động cho ứng dụng **AI Code Analyzer Desktop**.

Phạm vi chính:

- Ứng dụng JavaFX desktop.
- SQL Server local.
- Quản lý platform/handle.
- Crawl submission trực tiếp web.
- Phân tích source code bằng rule-based/Gemini REST/mock mode.
- Chấm điểm kỹ năng.
- Dashboard, scheduler, xuất PDF/Excel.

Lưu ý: dự án hiện chưa có màn hình đăng nhập người dùng theo kiểu web app. Vì vậy phần authentication/admin được kiểm thử theo phạm vi hiện có: quyền SQL Server, cấu hình secrets, quyền truy cập dữ liệu/crawl trực tiếp và thao tác quản trị trong ứng dụng desktop.

## 2. Test Authentication

| ID | Test case | Bước kiểm thử | Kết quả mong đợi |
|---|---|---|---|
| AUTH-001 | Kết nối DB đúng user/password | Set `DB_PASSWORD`, chạy app hoặc test connection | Kết nối SQL Server thành công |
| AUTH-002 | Sai mật khẩu DB | Set sai `DB_PASSWORD`, chạy app | App báo lỗi kết nối thân thiện, không crash |
| AUTH-003 | Thiếu biến môi trường DB | Xóa `DB_PASSWORD`, giữ `db.password=${DB_PASSWORD}` | App báo lỗi cấu hình/kết nối rõ ràng |
| AUTH-004 | Gemini API key hợp lệ | Set `GEMINI_API_KEY`, `ai.mock-mode=false`, phân tích source | Request AI chạy được, lưu kết quả analysis |
| AUTH-005 | Gemini API key sai | Set key sai, phân tích source | Hiển thị lỗi API, không lộ key trong UI/log |
| AUTH-006 | Không có API key | Không set `GEMINI_API_KEY` | App dùng mock/rule-based theo cấu hình, không gọi API thật |
| AUTH-007 | Không lưu secret trong repo | Search `DB_PASSWORD`, `GEMINI_API_KEY`, key thật | Chỉ có placeholder/env var, không có secret thật |

## 3. Test CRUD

| ID | Module | Test case | Bước kiểm thử | Kết quả mong đợi |
|---|---|---|---|---|
| CRUD-001 | Platform | Xem danh sách platform | Mở màn hình quản lý handle/platform | Có `CODEFORCES`, `VJUDGE` từ seed data |
| CRUD-002 | Handle | Thêm handle mới | Nhập platform, handle, display name, group, consent | Handle được lưu và hiển thị trong bảng |
| CRUD-003 | Handle | Validate handle trống | Bỏ trống handle, bấm save | App báo lỗi validate, không insert DB |
| CRUD-004 | Handle | Chặn handle trùng | Thêm lại cùng `platform + handle` | App/DB chặn trùng, báo lỗi dễ hiểu |
| CRUD-005 | Handle | Sửa thông tin handle | Chọn handle, đổi display name/group/status | Dữ liệu cập nhật đúng sau refresh |
| CRUD-006 | Handle | Xóa hoặc deactivate handle | Chọn handle test, thực hiện xóa/deactivate | Có confirm; dữ liệu không còn active hoặc bị xóa đúng ràng buộc |
| CRUD-007 | Submission | Lưu submission mới qua crawl | Crawl submission chưa tồn tại | Tạo bản ghi `submissions` và `source_codes` nếu có source |
| CRUD-008 | Submission | Upsert submission trùng | Crawl cùng nick nhiều lần | Không nhân đôi submission; count skipped/updated hợp lý |
| CRUD-009 | Analysis | Tạo analysis | Chọn source, chạy phân tích | Tạo bản ghi `ai_analysis_results` |
| CRUD-010 | Report data | Đọc dữ liệu báo cáo | Chọn khoảng ngày và handle | Báo cáo lấy đúng submission/score/analysis theo filter |

## 4. Test Direct Web Crawl

Các ca kiểm thử tập trung vào crawl trực tiếp từ web Codeforces/VJudge qua Chrome debug.

| ID | Test case | Dữ liệu | Bước kiểm thử | Kết quả mong đợi |
|---|---|---|---|---|
| CRAWL-001 | Chrome debug sẵn sàng | Nick public | Mở Chrome debug, đăng nhập, crawl | Crawl thành công, có source nếu được phép |
| CRAWL-002 | Chưa mở Chrome debug | Nick public | Không mở Chrome debug, crawl | App báo cần mở Chrome debug, không crash |
| CRAWL-003 | Login required | Nick yêu cầu login | Crawl khi chưa đăng nhập | Trạng thái `LOGIN_REQUIRED` hoặc `PERMISSION_DENIED` |
| CRAWL-004 | VJudge share code off | Nick VJudge | Crawl trực tiếp | Lưu metadata, source `SOURCE_NOT_AVAILABLE` |
| CRAWL-005 | OCR snapshot | Nick VJudge có snapshot | Có `GEMINI_API_KEY`, crawl | OCR thành công, lưu source |
| CRAWL-006 | Rate limit | Nick nhiều submissions | Crawl liên tục | Có retry, warning `RATE_LIMITED` |

## 5. Test Admin

| ID | Test case | Bước kiểm thử | Kết quả mong đợi |
|---|---|---|---|
| ADM-001 | Thiết lập database ban đầu | Chạy `sql/ai-code-analyzer-complete.sql` | Tạo đủ bảng, khóa, constraint, index |
| ADM-002 | Seed data | Chạy `sql/ai-code-analyzer-complete.sql` | Có platform, handle, submission, source, analysis, score demo |
| ADM-003 | Cấu hình AI provider | Sửa `application.properties` sang `gemini-rest` | App đọc đúng provider/model/endpoint |
| ADM-004 | Bật/tắt mock mode | Đổi `ai.mock-mode=true/false` | Chế độ phân tích thay đổi đúng |
| ADM-005 | Cấu hình scheduler | Bật auto crawl, chọn giờ chạy | Scheduler lưu cấu hình và cập nhật status |
| ADM-006 | Manual crawl | Bấm Crawl Now | Job chạy một lần, ghi crawl log |
| ADM-007 | Không chạy job chồng nhau | Trigger crawl liên tục | Không có 2 job cùng loại chạy song song |
| ADM-008 | Reset môi trường demo | Xóa/tạo lại DB, chạy schema + seed | App trở về trạng thái demo ổn định |

## 6. Test Responsive

Vì đây là JavaFX desktop app, responsive được hiểu là khả năng co giãn layout cửa sổ, bảng, form và text.

| ID | Test case | Bước kiểm thử | Kết quả mong đợi |
|---|---|---|---|
| RESP-001 | Màn hình nhỏ | Resize cửa sổ về khoảng 1024x768 | Không mất nút chính, không overlap text |
| RESP-002 | Màn hình rộng | Phóng to full screen | Dashboard/table tận dụng không gian, không bị lệch layout |
| RESP-003 | Sidebar/navigation | Resize ngang hẹp/rộng | Menu vẫn dùng được, text không bị cắt nghiêm trọng |
| RESP-004 | TableView nhiều cột | Mở submissions/source list | Có scroll hoặc auto width hợp lý |
| RESP-005 | Source code dài | Mở source 500+ dòng | Text area scroll mượt, không treo UI |
| RESP-006 | Thông báo lỗi dài | Gây lỗi network/API dài | Message wrap đúng, không phá layout |
| RESP-007 | DPI scaling Windows | Chạy với scaling 125%/150% | Control vẫn đọc được, không overlap |

## 7. Test Error Cases

| ID | Test case | Bước kiểm thử | Kết quả mong đợi |
|---|---|---|---|
| ERR-001 | SQL Server tắt | Stop SQL Server, chạy app | Báo lỗi kết nối, không crash |
| ERR-002 | Sai JDBC URL | Cấu hình port/database sai | Báo lỗi database rõ ràng |
| ERR-003 | Thiếu schema | Trỏ DB chưa chạy `ai-code-analyzer-complete.sql` | Báo lỗi thiếu bảng, không mất dữ liệu |
| ERR-004 | Network Gemini lỗi | Ngắt mạng hoặc dùng endpoint sai | Có retry theo cấu hình, báo lỗi thân thiện |
| ERR-005 | Gemini trả JSON sai schema | Mock response thiếu field | Không lưu analysis sai; báo lỗi parse/schema |
| ERR-006 | Codeforces API rate limit | Mock/trạng thái HTTP 429 | Retry/delay đúng; log warning |
| ERR-007 | Handle không tồn tại | Crawl handle invalid | Ghi crawl log failed, job không dừng toàn bộ |
| ERR-008 | Report không có dữ liệu | Chọn khoảng ngày rỗng | Export hoặc UI báo không có dữ liệu, không crash |
| ERR-009 | Không ghi được file report | Chọn folder không có quyền ghi | Báo lỗi quyền ghi, không treo app |
| ERR-010 | Dữ liệu Unicode | Handle/problem/source có tiếng Việt/ký tự đặc biệt | Lưu DB, hiển thị UI, export đúng |

## 8. Test Security Cases

| ID | Test case | Bước kiểm thử | Kết quả mong đợi |
|---|---|---|---|
| SEC-001 | Không lộ API key | Chạy analyzer với key giả, xem log/UI/report | Không xuất hiện key đầy đủ |
| SEC-002 | Không lộ DB password | Gây lỗi DB, xem message/log | Không in password |
| SEC-003 | SQL injection handle | Nhập `abc'; DROP TABLE dbo.platforms;--` | Dữ liệu được xử lý như text hoặc bị validate; bảng không bị ảnh hưởng |
| SEC-004 | SQL injection filter report | Nhập/filter chuỗi bất thường | Query an toàn, không lỗi SQL ngoài ý muốn |
 | SEC-005 | Chrome debug isolation | Mở Chrome debug profile riêng | App chỉ dùng session người dùng, không lưu cookie/token vào DB/log |
 | SEC-006 | Rate limit bảo vệ | Crawl liên tục nhiều nick | App có delay/retry giới hạn, không spam request |
 | SEC-007 | Không crawl dữ liệu riêng tư | VJudge/source private không có quyền | App không bypass login/cookie; lưu metadata và gắn trạng thái phù hợp |
| SEC-008 | Report không chứa secret | Mở PDF/Excel export | Không chứa DB URL password/API key/token |
| SEC-009 | Cấu hình thật không commit | Kiểm tra `.gitignore` và file config | `application.properties` thật được ignore; example chỉ placeholder |
| SEC-010 | AI risk wording | Source có risk cao | UI/report dùng ngôn ngữ xác suất, không kết luận gian lận chắc chắn |

## 9. Test Deployment

| ID | Test case | Bước kiểm thử | Kết quả mong đợi |
|---|---|---|---|
| DEP-001 | Build sạch | Chạy `mvn clean package` | Build success, tạo jar trong `target/` |
| DEP-002 | Chạy test trước release | Chạy `mvn test` | Tất cả test pass |
| DEP-003 | Chạy JavaFX app | Chạy `mvn javafx:run` | App mở được |
| DEP-004 | Máy mới | Clone/copy project sang máy khác | Làm theo `INSTALLATION.md` chạy được |
| DEP-005 | SQL Server local | Tạo DB, chạy schema/seed | App kết nối và đọc dashboard |
| DEP-006 | Không có internet | Tắt mạng, bật mock mode | App vẫn chạy các chức năng local/rule-based |
| DEP-007 | Có internet + Gemini | Set `GEMINI_API_KEY`, mock mode false | Phân tích AI thật hoạt động |
| DEP-008 | Package release | Kiểm tra file build không chứa secret | Artifact không đóng gói key/password thật |
| DEP-009 | Log runtime | Chạy app 15-30 phút | Không có exception lặp vô hạn hoặc log secret |

## 10. Checklist Test Thủ Công

### Chuẩn bị môi trường

- [ ] Cài JDK 21.
- [ ] Cài Maven 3.9+.
- [ ] Cài SQL Server 2019+/Express.
- [ ] Tạo database `CodeAnalyzerDb`.
- [ ] Chạy `sql/ai-code-analyzer-complete.sql`.
- [ ] Copy `src/main/resources/application.properties.example` thành `application.properties`.
- [ ] Set `DB_PASSWORD`.
- [ ] Set `GEMINI_API_KEY` nếu test AI thật.
- [ ] Chạy `mvn test` pass.

### Luồng chính

- [ ] Mở app bằng `mvn javafx:run`.
- [ ] Dashboard hiển thị được dữ liệu demo.
- [ ] Thêm handle mới thành công.
- [ ] Sửa handle thành công.
- [ ] Xóa/deactivate handle test thành công.
- [ ] Crawl trực tiếp Codeforces/VJudge thành công.
- [ ] Crawl Codeforces handle public thành công hoặc báo lỗi thân thiện.
- [ ] Mở chi tiết source code.
- [ ] Phân tích source bằng mock/rule-based.
- [ ] Phân tích source bằng Gemini API thật.
- [ ] Chấm điểm kỹ năng/hiển thị score.
- [ ] Bật/tắt scheduler.
- [ ] Chạy Crawl Now.
- [ ] Xuất PDF.
- [ ] Xuất Excel.
- [ ] Kiểm tra PDF/Excel không chứa secret.

### Regression nhanh trước demo

- [ ] `mvn clean package` pass.
- [ ] App mở không crash.
- [ ] DB connect được.
- [ ] Dashboard refresh được.
- [ ] Crawl trực tiếp ít nhất 1 nick mẫu.
- [ ] Analyze một source được.
- [ ] Export PDF/Excel được.
- [ ] Không có API key/password trong log hoặc report.

## 11. Test Data Cần Chuẩn Bị

### Database

- Database: `CodeAnalyzerDb`.
- Database script: `sql/ai-code-analyzer-complete.sql`.
- SQL login test: `code_analyzer_app`.
- Password đặt qua env var: `DB_PASSWORD`.

### Handles

- `demo_cf_alpha` - Codeforces.
- `demo_cf_beta` - Codeforces.
- `demo_cf_gamma` - Codeforces.
- `demo_vj_delta` - VJudge.
- `demo_vj_epsilon` - VJudge.
- 1 handle test mới để CRUD, ví dụ `qa_handle_001`.
- 1 handle invalid, ví dụ `handle_not_exist_qa_999999`.

### Submission/source

- Source C++ ngắn: `int main(){return 0;}`.
- Source C++ có `vector`, `sort`, `binary_search`.
- Source Java có `ArrayList`, `HashMap`.
- Source Python có loop/list/dict.
- Source dài 500+ dòng để test UI scroll.
- Source có Unicode/comment tiếng Việt.
- Submission duplicate cùng `platform + platform_submission_id`.

### Direct web crawl

- Chrome debug mở sẵn tại `http://localhost:9222`.
- 1 nick Codeforces public có thể xem source.
- 1 nick VJudge có submission bật Share code.
- 1 nick yêu cầu login để kiểm tra `LOGIN_REQUIRED`.
- 1 nick VJudge trả snapshot để test OCR (nếu có `GEMINI_API_KEY`).

### AI/API

- Gemini API key hợp lệ trong `GEMINI_API_KEY`.
- Gemini API key sai để test lỗi 401/403.
- `ai.mock-mode=true` để test offline.
- `ai.mock-mode=false` để test API thật.
- Endpoint sai để test network/API error.

### Report/export

- Khoảng ngày có dữ liệu: dùng đúng khoảng ngày trong `sql/ai-code-analyzer-complete.sql`.
- Khoảng ngày không có dữ liệu.
- Folder export có quyền ghi.
- Folder/path không có quyền ghi để test lỗi.

### Security

- Input SQL injection mẫu: `abc'; DROP TABLE dbo.platforms;--`.
- Input dài 1.000+ ký tự.
- Handle có ký tự lạ/khoảng trắng.
- Session Chrome debug mở bằng profile riêng (không dùng profile cá nhân).
- Key giả dạng `AIzaSy_TEST_SHOULD_NOT_LEAK`.
