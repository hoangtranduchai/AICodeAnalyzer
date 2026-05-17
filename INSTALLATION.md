# INSTALLATION - Windows

Tài liệu này hướng dẫn cài và chạy dự án **AI Code Analyzer Desktop** trên Windows. Các bước được viết theo kiểu dễ làm theo cho sinh viên/người mới.

## 1. Cài JDK

Dự án đang dùng Java 21 trong `pom.xml`, vì vậy nên cài **JDK 21**. Nếu máy đã có JDK 17 thì có thể dùng được sau khi chỉnh `maven.compiler.release`, nhưng khuyến nghị giữ JDK 21.

Các bước:

1. Tải JDK 21 từ một nhà cung cấp tin cậy, ví dụ Temurin, Oracle JDK, Microsoft Build of OpenJDK.
2. Cài đặt JDK như phần mềm Windows bình thường.
3. Thêm biến môi trường:
   - `JAVA_HOME`: trỏ tới thư mục JDK, ví dụ `C:\Program Files\Java\jdk-21`
   - Thêm `%JAVA_HOME%\bin` vào `Path`

Kiểm tra trong PowerShell:

```powershell
java -version
javac -version
```

Kết quả mong đợi là version Java 21.x.

## 2. Cài Maven

Maven dùng để tải thư viện, build project và chạy JavaFX app.

Các bước:

1. Tải Apache Maven 3.9+.
2. Giải nén vào một thư mục dễ nhớ, ví dụ `C:\Maven\apache-maven-3.9.15`.
3. Thêm biến môi trường:
   - `MAVEN_HOME`: trỏ tới thư mục Maven
   - Thêm `%MAVEN_HOME%\bin` vào `Path`

Kiểm tra:

```powershell
mvn -version
```

Nếu Maven nhận đúng JDK 21 thì phần `Java version` trong output sẽ là 21.x.

## 3. Cài SQL Server

Bạn có thể dùng **SQL Server Developer** hoặc **SQL Server Express** cho đồ án.

Khuyến nghị cho sinh viên:

- SQL Server Express: nhẹ, đủ cho demo.
- SQL Server Developer: đầy đủ tính năng, phù hợp nếu muốn thử nhiều chức năng hơn.

Khi cài đặt:

1. Chọn `Basic` nếu muốn cài nhanh.
2. Ghi nhớ tên instance:
   - Instance mặc định thường dùng `localhost`
   - SQL Server Express thường dùng `localhost\SQLEXPRESS`
3. Bật chế độ đăng nhập SQL Server nếu muốn dùng user/password riêng:
   - Mở SQL Server Management Studio.
   - Chuột phải server -> `Properties` -> `Security`.
   - Chọn `SQL Server and Windows Authentication mode`.
   - Restart SQL Server service.

## 4. Cài SQL Server Management Studio

SQL Server Management Studio, viết tắt là SSMS, dùng để tạo database và chạy script SQL.

Các bước:

1. Tải SSMS từ trang Microsoft.
2. Cài đặt theo wizard.
3. Mở SSMS.
4. Kết nối tới server:
   - Nếu dùng instance mặc định: `localhost`
   - Nếu dùng SQL Server Express: `localhost\SQLEXPRESS`
   - Authentication: chọn `Windows Authentication` lúc đầu cho dễ cấu hình.

## 5. Tạo database

Mở SSMS, bấm `New Query`, chạy:

```sql
CREATE DATABASE CodeAnalyzerDb;
GO
```

Sau khi chạy thành công, kiểm tra bên trái trong `Object Explorer`:

```text
Databases
└─ CodeAnalyzerDb
```

## 6. Chạy script database hoàn chỉnh

Script database hoàn chỉnh của dự án hiện nằm tại:

```text
E:\CrawlCode\ai-code-analyzer-desktop\sql\ai-code-analyzer-complete.sql
```

Trong SSMS:

1. Mở file `ai-code-analyzer-complete.sql`.
2. Bấm `Execute`.

Script này tự tạo `CodeAnalyzerDb` nếu chưa có, tạo schema/index và seed dữ liệu demo để test dashboard, bảng thống kê và báo cáo.

## 7. Tạo tài khoản SQL cho ứng dụng

Không nên dùng tài khoản `sa` trong ứng dụng. Hãy tạo user riêng:

```sql
USE master;
GO

CREATE LOGIN code_analyzer_app
WITH PASSWORD = 'Change_This_Strong_Password_123!';
GO

USE CodeAnalyzerDb;
GO

CREATE USER code_analyzer_app FOR LOGIN code_analyzer_app;
CREATE ROLE code_analyzer_app_role;

GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.platforms TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.programming_handles TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.submissions TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.source_codes TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.ai_analysis_results TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.user_skill_scores TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.crawl_logs TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.app_settings TO code_analyzer_app_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.error_logs TO code_analyzer_app_role;

ALTER ROLE code_analyzer_app_role ADD MEMBER code_analyzer_app;
GO
```

Bạn có thể đổi password mạnh hơn. Sau khi đổi, nhớ cấu hình lại biến môi trường `DB_PASSWORD`.

## 8. Cấu hình `application.properties`

Trong thư mục project:

```text
E:\CrawlCode\ai-code-analyzer-desktop
```

Copy file mẫu:

```powershell
Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties
```

Mở file:

```text
src/main/resources/application.properties
```

Cấu hình theo SQL Server của bạn.

Nếu dùng server mặc định qua port 1433:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=CodeAnalyzerDb;encrypt=true;trustServerCertificate=true
db.username=code_analyzer_app
db.password=${DB_PASSWORD}
db.login-timeout-seconds=10
```

Nếu dùng SQL Server Express named instance:

```properties
db.url=jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=CodeAnalyzerDb;encrypt=true;trustServerCertificate=true
db.username=code_analyzer_app
db.password=${DB_PASSWORD}
db.login-timeout-seconds=10
```

Lưu ý quan trọng:

- Trong file `.properties`, ký tự `\` nên viết thành `\\`.
- Không commit `src/main/resources/application.properties` vì file này có thể chứa thông tin máy local hoặc mật khẩu.
- File `.gitignore` của project đã ignore `application.properties`.

Cấu hình password bằng biến môi trường trong PowerShell:

```powershell
$env:DB_PASSWORD="Change_This_Strong_Password_123!"
```

Lệnh trên chỉ có hiệu lực trong cửa sổ PowerShell hiện tại. Nếu muốn lưu lâu dài:

```powershell
setx DB_PASSWORD "Change_This_Strong_Password_123!"
```

Sau khi dùng `setx`, hãy mở PowerShell mới để biến môi trường có hiệu lực.

## 9. Cấu hình `GEMINI_API_KEY` nếu dùng AI

Ứng dụng có thể chạy rule-based analyzer khi không có API key. Nếu muốn dùng Gemini API, cấu hình:

```powershell
$env:GEMINI_API_KEY="your_api_key_here"
```

Hoặc lưu lâu dài:

```powershell
setx GEMINI_API_KEY "your_api_key_here"
```

Sau đó mở PowerShell mới.

Trong `application.properties`, giữ cấu hình như sau:

```properties
ai.provider=gemini-rest
ai.api-key-env=GEMINI_API_KEY
ai.api-key=
ai.model=gemini-2.5-flash
ai.endpoint=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
ai.timeout-seconds=45
ai.max-retries=3
ai.mock-mode=false
```

Không nên ghi trực tiếp API key vào `ai.api-key`. Nếu bắt buộc dùng tạm khi demo offline, tuyệt đối không commit file đó.

Nếu không dùng API thật, có thể bật mock mode để test UI:

```properties
ai.mock-mode=true
```

## 10. Build project

Mở PowerShell tại thư mục project:

```powershell
cd E:\CrawlCode\ai-code-analyzer-desktop
```

Chạy test:

```powershell
mvn test
```

Build:

```powershell
mvn clean package
```

Nếu build thành công, Maven sẽ tạo thư mục:

```text
target/
```

File jar thực thi đầy đủ dependency nằm tại:

```text
target/ai-code-analyzer-desktop-1.0.0-SNAPSHOT-all.jar
```

## 11. Chạy ứng dụng

Cách khuyến nghị khi phát triển:

```powershell
mvn javafx:run
```

Nếu mọi thứ đúng, cửa sổ JavaFX của **AI Code Analyzer Desktop** sẽ mở lên.

Cách chạy artifact sau khi build:

```powershell
java -jar target/ai-code-analyzer-desktop-1.0.0-SNAPSHOT-all.jar
```

Nếu chạy jar ở thư mục khác, đặt `application.properties` cạnh lệnh chạy hoặc chỉ định đường dẫn:

```powershell
java -Dapp.config="E:\CrawlCode\ai-code-analyzer-desktop\src\main\resources\application.properties" -jar target/ai-code-analyzer-desktop-1.0.0-SNAPSHOT-all.jar
```

Nếu muốn tạo file `.exe` để gửi cho người dùng cuối, dùng `jpackage` của JDK:

```powershell
jpackage --type exe --name AI-Code-Analyzer --input target --main-jar ai-code-analyzer-desktop-1.0.0-SNAPSHOT-all.jar --main-class com.example.aicodeanalyzer.app.DesktopLauncher --dest dist
```

Quy trình test nhanh trong app:

1. Mở `Workspace`.
2. Bấm `Initialize Browser Bot` / `Khởi tạo Chrome bot`.
3. Đăng nhập Codeforces/VJudge bằng tài khoản phụ, Codeforces bắt buộc có rating và không dùng nick unrated.
4. Thêm nick trong `Quick Add Handle` / `Thêm nick nhanh`.
5. Chạy `Crawl & Analyze Now` / `Crawl & phân tích ngay`, hoặc bấm `Crawl` trên từng dòng nick, hoặc bật lịch crawl hằng ngày trong `Settings & Guide`.

Tùy chọn ngôn ngữ giao diện:

```powershell
mvn javafx:run "-Dapp.language=vi"
mvn javafx:run "-Dapp.language=en"
```

Trong app có thể đổi ngay bằng nút `VI/EN` ở cuối sidebar bên trái.

6. Mở `AI Review`, phân tích AI/rule-based nếu cần xem chi tiết từng source.
7. Xem tổng quan trong `Workspace`, mở `Reports` để đánh giá và xuất báo cáo.

## 12. Lỗi thường gặp và cách sửa

### `java` hoặc `javac` không nhận lệnh

Nguyên nhân:

- Chưa cài JDK.
- Chưa cấu hình `JAVA_HOME`.
- Chưa thêm `%JAVA_HOME%\bin` vào `Path`.

Cách sửa:

```powershell
echo $env:JAVA_HOME
java -version
javac -version
```

Nếu không ra version, cấu hình lại biến môi trường rồi mở PowerShell mới.

### Maven dùng sai Java version

Kiểm tra:

```powershell
mvn -version
```

Nếu Maven đang dùng Java 8/11/17 trong khi project yêu cầu 21, sửa `JAVA_HOME` trỏ tới JDK 21.

### `mvn` không nhận lệnh

Nguyên nhân:

- Chưa cài Maven.
- Chưa thêm Maven `bin` vào `Path`.

Cách sửa:

```powershell
echo $env:MAVEN_HOME
mvn -version
```

Sau khi sửa environment variables, mở PowerShell mới.

### Không kết nối được SQL Server

Thông báo thường gặp:

```text
Cannot reach SQL Server
Connection refused
The connection to the host localhost, port 1433 has failed
```

Cách sửa:

1. Mở `SQL Server Configuration Manager`.
2. Vào `SQL Server Network Configuration`.
3. Bật `TCP/IP`.
4. Nếu dùng port 1433, vào `TCP/IP Properties` -> `IPAll` -> đặt `TCP Port = 1433`.
5. Restart SQL Server service.
6. Kiểm tra firewall nếu vẫn không kết nối được.

### Lỗi `Login failed for user`

Nguyên nhân:

- Sai username/password.
- Chưa bật SQL Server Authentication mode.
- Chưa tạo user trong database.
- Biến môi trường `DB_PASSWORD` chưa có hiệu lực.

Cách sửa:

```powershell
echo $env:DB_PASSWORD
```

Nếu rỗng, set lại:

```powershell
$env:DB_PASSWORD="your_password"
```

Sau đó kiểm tra trong SSMS:

```sql
USE CodeAnalyzerDb;
SELECT name FROM sys.database_principals WHERE name = 'code_analyzer_app';
```

### Lỗi certificate/encrypt khi kết nối SQL Server

Với môi trường local, giữ đoạn sau trong JDBC URL:

```properties
encrypt=true;trustServerCertificate=true
```

Ví dụ:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=CodeAnalyzerDb;encrypt=true;trustServerCertificate=true
```

### Lỗi `Cannot find application.properties`

Nguyên nhân:

- Chưa copy file cấu hình.

Cách sửa:

```powershell
Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties
```

Sau đó chỉnh lại `db.url`, `db.username`, `db.password`.

### Lỗi `Database not found` hoặc `Cannot open database CodeAnalyzerDb`

Nguyên nhân:

- Chưa tạo database.
- Tên database trong `db.url` sai.

Cách sửa trong SSMS:

```sql
SELECT name FROM sys.databases WHERE name = 'CodeAnalyzerDb';
```

Nếu không có kết quả, chạy lại:

```sql
CREATE DATABASE CodeAnalyzerDb;
GO
```

### Lỗi thiếu bảng như `Invalid object name dbo.platforms`

Nguyên nhân:

- Chưa chạy `ai-code-analyzer-complete.sql`.
- Chạy script trên nhầm database.

Cách sửa:

1. Mở `sql/ai-code-analyzer-complete.sql`.
2. Chạy lại toàn bộ script trong SSMS.

### Chạy app nhưng AI không hoạt động

Nguyên nhân:

- Chưa cấu hình `GEMINI_API_KEY`.
- API key sai.
- Máy không có internet.
- `ai.mock-mode=true`.

Cách kiểm tra:

```powershell
echo $env:GEMINI_API_KEY
```

Nếu không muốn dùng API thật, đặt:

```properties
ai.mock-mode=true
```

Khi đó app vẫn chạy được để demo giao diện và luồng phân tích giả lập.

### JavaFX không mở cửa sổ

Thử chạy:

```powershell
mvn clean javafx:run
```

Nếu vẫn lỗi, kiểm tra:

- JDK đúng version.
- Maven tải dependency thành công.
- Không bị proxy/firewall chặn Maven Central.

### Maven tải dependency rất chậm hoặc lỗi network

Cách xử lý:

1. Kiểm tra internet.
2. Chạy lại:

```powershell
mvn -U clean test
```

3. Nếu dùng mạng trường học/công ty có proxy, cần cấu hình proxy trong Maven `settings.xml`.

## 13. Checklist trước khi demo

Trước khi demo đồ án, kiểm tra nhanh:

- `java -version` chạy được.
- `mvn -version` chạy được.
- SQL Server đang chạy.
- SSMS kết nối được `CodeAnalyzerDb`.
- Đã chạy `sql/ai-code-analyzer-complete.sql`.
- `application.properties` đã đúng JDBC URL.
- `DB_PASSWORD` đã được set.
- Nếu dùng AI thật, `GEMINI_API_KEY` đã được set.
- `mvn test` pass.
- `mvn javafx:run` mở được ứng dụng.
- Không commit API key, DB password, hoặc file `application.properties` thật.
