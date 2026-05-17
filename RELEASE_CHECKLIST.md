# RELEASE CHECKLIST - AI Code Analyzer Desktop

Checklist này dùng để kiểm tra trước khi bàn giao, demo hoặc nộp đồ án. Mỗi mục nên được tick sau khi đã kiểm chứng thực tế, không chỉ dựa trên giả định.

## 1. Thông tin bản bàn giao

| Mục | Nội dung |
|---|---|
| Tên dự án | AI Code Analyzer Desktop |
| Phiên bản bàn giao | |
| Ngày kiểm tra | |
| Người kiểm tra | |
| Môi trường kiểm tra | Windows, JDK 17/21, Maven, SQL Server |
| Ghi chú | |

## 2. Build và chạy ứng dụng

- [ ] Chạy `mvn clean package` thành công.
- [ ] Chạy `mvn javafx:run` mở được ứng dụng.
- [ ] Không có lỗi dependency Maven.
- [ ] Không có lỗi JavaFX runtime/module khi khởi động.
- [ ] Ứng dụng hiển thị được màn hình chính và sidebar.

Minh chứng cần lưu:

- Log build thành công.
- Ảnh màn hình ứng dụng mở thành công nếu cần.

## 3. Test

- [ ] Unit test chạy thành công.
- [ ] Integration test quan trọng chạy thành công.
- [ ] Repository test kiểm tra CRUD cơ bản.
- [ ] Analyzer test không gọi API thật.
- [ ] Crawler test dùng mock HTTP response.
- [ ] Scheduler test kiểm tra không chạy song song job crawl.
- [ ] Report export test tạo được file đầu ra.

Lệnh gợi ý:

```powershell
mvn test
```

Minh chứng cần lưu:

- Kết quả test pass.
- Danh sách test fail nếu còn tồn tại và lý do chấp nhận nếu có.

## 4. Database

- [ ] Script tạo database chạy được trên SQL Server Management Studio.
- [ ] Script tạo bảng chạy không lỗi.
- [ ] Primary key, foreign key và unique constraint hoạt động đúng.
- [ ] Unique constraint `platform + handle` hoạt động đúng.
- [ ] Unique constraint `platform + remote_submission_id` hoặc logic chống trùng submission hoạt động đúng.
- [ ] Index cần thiết đã được tạo.
- [ ] Có bảng `crawl_logs`.
- [ ] Có bảng `ai_analysis_results`.
- [ ] Có bảng `user_skill_scores`.
- [ ] Dữ liệu demo insert được thành công.
- [ ] Ứng dụng kết nối được tới database demo.

Minh chứng cần lưu:

- File schema SQL.
- File insert dữ liệu demo.
- Ảnh hoặc log chạy script thành công.

## 5. Bảo mật cấu hình

- [ ] Không commit `src/main/resources/application.properties` chứa thông tin thật.
- [ ] Có `application.properties.example` để hướng dẫn cấu hình.
- [ ] Không hard-code OpenAI API key trong source code.
- [ ] Không hard-code password database trong source code.
- [ ] Không commit file `.env` chứa secret thật.
- [ ] `.gitignore` đã loại trừ `application.properties`, `.env`, logs, reports và file database local.
- [ ] Log không in API key.
- [ ] Log không in password database.
- [ ] Log không in toàn bộ source code nhạy cảm.
- [ ] README/INSTALLATION hướng dẫn dùng biến môi trường cho secret.

Kiểm tra nhanh:

```powershell
rg -n "sk-|OPENAI_API_KEY|password=|db.password|api.key" .
```

Lưu ý: nếu kết quả chỉ xuất hiện trong file mẫu hoặc tài liệu hướng dẫn dạng placeholder thì có thể chấp nhận. Nếu xuất hiện secret thật, phải xóa trước khi bàn giao.

## 6. Crawler trực tiếp web

- [ ] Codeforces crawler dùng endpoint công khai và đọc source từ trang submission.
- [ ] VJudge crawl trực tiếp qua Chrome debug session, không import file.
- [ ] Có rate limit khi gọi nguồn dữ liệu bên ngoài.
- [ ] Có retry giới hạn, ví dụ tối đa 3 lần.
- [ ] Có xử lý lỗi từng handle, không làm dừng toàn bộ job.
- [ ] Nếu không lấy được source code hợp lệ thì lưu metadata và đánh dấu `SOURCE_NOT_AVAILABLE`.
- [ ] Có ghi log kết quả crawl vào `crawl_logs`.
- [ ] Có thống kê `new_count`, `updated_count`, `skipped_count`, `failed_count`.
- [ ] Có cảnh báo người dùng chỉ crawl dữ liệu công khai hoặc có quyền truy cập hợp lệ.

Minh chứng cần lưu:

- Log một lần crawl trực tiếp thành công.
- Log một trường hợp lỗi được xử lý an toàn.

## 7. AI Analyzer

- [ ] OpenAI analyzer đọc API key từ biến môi trường hoặc cấu hình ngoài source code.
- [ ] Không log API key.
- [ ] Có timeout khi gọi API.
- [ ] Có retry khi lỗi mạng.
- [ ] Có mock mode hoặc test mode để không gọi API thật khi test.
- [ ] Có fallback `RuleBasedCodeAnalyzer` khi không có API key.
- [ ] Kết quả trả về đúng JSON schema đã thiết kế.
- [ ] Với source code quá ngắn, confidence giảm hợp lý.
- [ ] Kết quả AI usage chỉ ghi là xác suất/dấu hiệu cần kiểm chứng thêm, không kết luận chắc chắn.

Minh chứng cần lưu:

- Ảnh hoặc log phân tích một source code thành công.
- Ảnh hoặc log fallback heuristic hoạt động khi không có API key.

## 8. UI và trải nghiệm người dùng

- [ ] Màn hình Bat dau hiển thị hướng dẫn rõ ràng cho người mới.
- [ ] Nhập nick Codeforces/VJudge và crawl trực tiếp hoạt động.
- [ ] Bảng kết quả crawl và danh sách nick hiển thị đúng.
- [ ] Màn hình Phan tich xử lý source code dài.
- [ ] Nút copy source code hoạt động.
- [ ] Nút gửi phân tích AI hoạt động hoặc báo lỗi thân thiện.
- [ ] Màn hình Danh gia & Bao cao hiển thị preview và bảng điểm.
- [ ] Màn hình Cai dat bật/tắt auto crawl và lưu lịch.
- [ ] UI không crash khi bảng không có dòng nào.
- [ ] Thông báo lỗi/thành công rõ ràng cho người dùng.

Minh chứng cần lưu:

- Ảnh màn hình Bat dau.
- Ảnh màn hình Phan tich.
- Ảnh màn hình Danh gia & Bao cao.
- Ảnh màn hình Cai dat hoặc file report đã xuất.

## 9. Scheduler

- [ ] ScheduledExecutorService daily workflow chạy được theo lịch.
- [ ] Có thể cấu hình giờ chạy hằng ngày trong UI.
- [ ] Có thể bật/tắt auto crawl.
- [ ] Crawl thủ công chạy từ màn hình Bat dau.
- [ ] Không chạy song song hai job crawl cùng lúc.
- [ ] Lỗi của một handle không làm dừng toàn bộ job.
- [ ] Có ghi log mỗi lần scheduler chạy.
- [ ] Hiển thị lần chạy gần nhất và kết quả gần nhất.

## 10. Báo cáo

- [ ] Xuất báo cáo PDF thành công.
- [ ] Xuất báo cáo Excel thành công nếu có hỗ trợ.
- [ ] Báo cáo hỗ trợ tiếng Việt Unicode.
- [ ] Báo cáo có bảng điểm từng handle.
- [ ] Báo cáo có nhận xét từng handle.
- [ ] Báo cáo có khoảng thời gian lọc dữ liệu.
- [ ] Báo cáo có danh sách handle được chọn.
- [ ] File được lưu vào thư mục `reports`.
- [ ] Có thể mở thư mục/file sau khi xuất.

Minh chứng cần lưu:

- Một file PDF demo.
- Một file Excel demo nếu có.

## 11. Tài liệu bàn giao

- [ ] `README.md` đầy đủ: giới thiệu, tính năng, công nghệ, cấu trúc, cài đặt, chạy dự án.
- [ ] `INSTALLATION.md` đầy đủ cho người dùng Windows.
- [ ] `USER_GUIDE.md` đầy đủ hướng dẫn thao tác ứng dụng.
- [ ] Có schema SQL.
- [ ] Có script dữ liệu mẫu.
- [ ] Có test plan hoặc mô tả test case.
- [ ] Có cảnh báo pháp lý/đạo đức về crawl dữ liệu.
- [ ] Có cảnh báo giới hạn của đánh giá AI-generated code.
- [ ] Có hướng dẫn cấu hình API key và database password an toàn.

## 12. Dữ liệu demo

- [ ] Có 2 platform demo: Codeforces, VJudge.
- [ ] Có ít nhất 5 handle demo.
- [ ] Có submission demo đủ để xem dashboard.
- [ ] Có source code demo bằng C++/Java/Python.
- [ ] Có kết quả AI analysis demo.
- [ ] Có điểm năng lực demo.
- [ ] Có mức AI usage risk khác nhau để test báo cáo.
- [ ] Dữ liệu demo được ghi rõ là giả lập, không khẳng định là dữ liệu thật.

## 13. Video/hình ảnh minh họa

- [ ] Có ảnh màn hình Dashboard nếu cần.
- [ ] Có ảnh màn hình quản lý handle nếu cần.
- [ ] Có ảnh màn hình xem source code nếu cần.
- [ ] Có ảnh màn hình báo cáo nếu cần.
- [ ] Có video demo luồng chính nếu giảng viên/yêu cầu bàn giao cần.

Luồng video demo gợi ý:

1. Mở ứng dụng.
2. Nhập nick trên màn hình Bat dau.
3. Chạy crawl trực tiếp.
4. Xem source code.
5. Phân tích AI.
6. Xem bảng điểm.
7. Xuất báo cáo.

## 14. Kiểm tra lần cuối trước khi nộp

- [ ] Xóa file cấu hình thật khỏi thư mục bàn giao nếu không cần thiết.
- [ ] Xóa API key/password khỏi lịch sử chat, ảnh chụp, log và tài liệu.
- [ ] Kiểm tra `.gitignore`.
- [ ] Chạy lại build.
- [ ] Chạy lại test.
- [ ] Mở ứng dụng bằng bộ dữ liệu demo.
- [ ] Xuất thử báo cáo.
- [ ] Nén đúng thư mục dự án cần bàn giao.
- [ ] Ghi rõ tài khoản/mật khẩu demo nếu có, nhưng không dùng secret thật.

## 15. Kết luận bàn giao

| Hạng mục | Đạt/Không đạt | Ghi chú |
|---|---|---|
| Build | | |
| Test | | |
| Database | | |
| Security | | |
| Crawler Direct Web | | |
| AI Analyzer | | |
| UI | | |
| Report | | |
| Documentation | | |
| Demo data | | |

Người kiểm tra xác nhận:

- Họ tên:
- Ngày:
- Chữ ký:
