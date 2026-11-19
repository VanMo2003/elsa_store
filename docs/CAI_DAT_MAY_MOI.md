# Cài đặt Elsa Store trên máy mới — từ đầu đến cuối

> **Dành cho trợ lý AI (Claude Code) chạy trên máy mới.** Làm lần lượt từng bước, chạy lệnh và kiểm tra kết quả ở mục
> "Kiểm tra" trước khi sang bước sau. Máy đích giả định là **Windows 10/11**. Mọi lệnh viết cho **PowerShell** trừ khi ghi khác.
> Nếu một bước cần người dùng thao tác (cài bằng giao diện, nhập mật khẩu, bấm đồng ý) thì dừng lại và nhờ người dùng làm.
> Không commit / push gì lên GitHub từ máy này trừ khi người dùng yêu cầu.

Dự án gồm 2 repo:

| Phần | Repo | Nhánh dùng | Công nghệ |
|---|---|---|---|
| Backend (BE) | `https://github.com/VanMo2003/elsa_store.git` | `develop` | Spring Boot 3.5.6, Java 21, Maven, MySQL 8 |
| App (FE) | `https://github.com/nangduan/elsa_store_fe.git` | `nang-cap` | Flutter **3.47.8** (Dart 3.13), Android + Web |

Kết quả cuối cùng: BE chạy ở `http://localhost:8080/elsa-store/api`, app chạy trên **Android emulator** (và điện thoại thật
nếu muốn) và trên **web (Chrome)**, đăng nhập được bằng tài khoản quản trị `admin123` / `admin123`.

---

## Bước 0 — Việc làm ở MÁY CŨ trước khi gửi file này (người dùng tự làm)

Code mới nhất đang nằm ở máy cũ, **chưa có trên GitHub**. Phải đẩy lên trước, nếu không máy mới sẽ clone về code cũ.

```powershell
# BE: nhánh develop
cd D:\datn_2026\bao\elsa_store
git status            # nếu còn file sửa dở, commit hoặc bỏ trước
git push origin develop

# FE: nhánh nang-cap (chưa từng đẩy lên)
cd D:\datn_2026\bao\elsa_store_fe
git status            # có 2 file chỉnh cỡ chữ đang sửa dở: commit nếu muốn mang sang máy mới
git push -u origin nang-cap
```

Kiểm tra trên GitHub: repo BE có nhánh `develop` mới nhất, repo FE có nhánh `nang-cap`.

> Các tệp bí mật **không** nằm trong repo và không cần cho việc chạy thử: file service account Firebase (push) và
> `google-services.json`. Thiếu chúng thì app vẫn chạy, chỉ không có thông báo đẩy (push). Khoá AI (Gemini) cũng không bắt
> buộc — thiếu thì các nút AI tự ẩn / dùng mẫu soạn sẵn.

---

## Bước 1 — Cài phần mềm

Ưu tiên cài bằng `winget` (có sẵn trên Windows 10/11). Sau mỗi lần cài, **mở PowerShell mới** để PATH được cập nhật.

### 1.1 Git

```powershell
winget install --id Git.Git -e
```

Kiểm tra: `git --version`.

### 1.2 JDK 21 (cho BE)

```powershell
winget install --id Microsoft.OpenJDK.21 -e
```

Kiểm tra: `java -version` ra `21.x`. Nếu không nhận, đặt biến môi trường `JAVA_HOME` trỏ tới thư mục JDK
(thường `C:\Program Files\Microsoft\jdk-21...`) và thêm `%JAVA_HOME%\bin` vào `Path`.

> Maven **không cần cài riêng**: repo BE có sẵn Maven Wrapper (`mvnw.cmd`), lần chạy đầu tự tải Maven.

### 1.3 MySQL 8.0

```powershell
winget install --id Oracle.MySQL -e
```

Nếu `winget` không có hoặc lỗi: tải **MySQL Installer 8.0** ở https://dev.mysql.com/downloads/installer/ và chọn
"Server only". Trong lúc cài, **đặt mật khẩu cho `root`** (nhờ người dùng chọn và ghi nhớ — dùng ở Bước 3).
Cổng mặc định `3306`.

Kiểm tra (đổi đường dẫn nếu khác):

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "select version();"
```

### 1.4 Flutter 3.47.8

Dự án ghim đúng bản **3.47.8** (các gói trong `pubspec.lock` đi kèm bản này). Cài bằng git cho chắc đúng bản:

```powershell
git clone https://github.com/flutter/flutter.git C:\flutter
cd C:\flutter
git checkout 3.47.8
```

Thêm `C:\flutter\bin` vào biến môi trường `Path` (User), mở PowerShell mới rồi:

```powershell
flutter --version      # phải ra Flutter 3.47.8, Dart 3.13
flutter config --enable-web
```

### 1.5 Android Studio + Android SDK + emulator

```powershell
winget install --id Google.AndroidStudio -e
```

Mở Android Studio một lần (nhờ người dùng bấm qua trình hướng dẫn **Standard**) để nó tải Android SDK, Platform-Tools,
Build-Tools, Emulator. Sau đó trong Android Studio: **More Actions → SDK Manager → SDK Tools** tích thêm
**Android SDK Command-line Tools (latest)** rồi Apply.

```powershell
flutter doctor --android-licenses     # gõ y để chấp nhận tất cả
flutter doctor
```

`flutter doctor` cần dấu ✓ cho: Flutter, Android toolchain, Chrome (web). Mục Visual Studio có thể bỏ qua (chỉ cần cho app
Windows desktop).

Tạo máy ảo: Android Studio → **More Actions → Virtual Device Manager → Create Device** → chọn **Pixel 7** (hoặc bất kỳ) →
system image **API 34/35/36** → Finish.

### 1.6 Google Chrome (để chạy bản web)

```powershell
winget install --id Google.Chrome -e
```

---

## Bước 2 — Clone code

Đặt 2 repo **cạnh nhau** trong cùng một thư mục (ví dụ `D:\elsa`):

```powershell
mkdir D:\elsa
cd D:\elsa
git clone -b develop https://github.com/VanMo2003/elsa_store.git
git clone -b nang-cap https://github.com/nangduan/elsa_store_fe.git
```

Kiểm tra: `git -C D:\elsa\elsa_store branch --show-current` ra `develop`, `git -C D:\elsa\elsa_store_fe branch --show-current`
ra `nang-cap`. Nếu clone báo không có nhánh → Bước 0 chưa làm.

---

## Bước 3 — Tạo cơ sở dữ liệu

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "CREATE DATABASE IF NOT EXISTS elsa_store_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Chỉ cần schema trống: BE tự tạo bảng khi chạy (Hibernate `ddl-auto=update`) và tự tạo tài khoản quản trị
`admin123` / `admin123`.

---

## Bước 4 — Cấu hình và chạy Backend

BE đọc cấu hình từ **biến môi trường** (không sửa file `application.yaml`). Tối thiểu cần tài khoản MySQL.

Đặt biến môi trường **vĩnh viễn** cho user (chạy một lần, rồi mở PowerShell mới):

```powershell
setx DB_URL "jdbc:mysql://localhost:3306/elsa_store_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"
setx DB_USERNAME "root"
setx DB_PASSWORD "<mật khẩu root MySQL đã đặt ở 1.3>"
```

Tuỳ chọn (có thể bỏ qua khi chạy thử):

| Biến | Dùng cho | Lấy ở đâu |
|---|---|---|
| `GEMINI_API_KEY` | Trợ lý AI (tư vấn, nhận xét báo cáo, viết mô tả…) | https://aistudio.google.com/apikey |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gửi email đơn hàng, mã OTP quên mật khẩu (thiếu thì mã OTP in ra log BE) | Gmail → Bảo mật → Mật khẩu ứng dụng |
| `FIREBASE_CREDENTIALS` | Thông báo đẩy (push) | Đường dẫn file JSON service account Firebase (người dùng gửi riêng, không có trong repo) |
| `JWT_SIGNER_KEY` | Khoá ký đăng nhập (bắt buộc khi đưa lên mạng thật) | Chuỗi ngẫu nhiên ≥ 64 ký tự |

Chạy BE:

```powershell
cd D:\elsa\elsa_store
.\mvnw.cmd spring-boot:run "-Dspotless.apply.skip=true"
```

- Lần đầu mất vài phút để tải thư viện Maven. Để cửa sổ này chạy suốt (BE dừng khi đóng cửa sổ).
- `-Dspotless.apply.skip=true` tránh việc Maven tự định dạng lại toàn bộ mã nguồn mỗi lần chạy.
- Có thể mở thư mục `elsa_store` bằng IntelliJ IDEA và chạy `ElsaStoreApplication` (đặt 3 biến `DB_*` ở
  *Run → Edit Configurations → Environment variables*).

**Kiểm tra** (PowerShell khác):

```powershell
curl.exe http://localhost:8080/elsa-store/api/ai/status
curl.exe -X POST http://localhost:8080/elsa-store/api/auth/login -H "Content-Type: application/json" -d '{\"username\":\"admin123\",\"password\":\"admin123\"}'
```

Lệnh thứ hai phải trả JSON có `"accessToken"`. Log BE không được có `Access denied for user` hay `Communications link failure`
(nếu có: sai `DB_PASSWORD` hoặc MySQL chưa chạy).

> Cổng 8080 bị chiếm thì đặt thêm `setx SERVER_PORT 8081` — khi đó mọi địa chỉ BE ở dưới đổi 8080 → 8081 và app phải chạy kèm
> `--dart-define=API_BASE_URL=...` (xem Bước 6).

---

## Bước 5 — Chuẩn bị app Flutter

```powershell
cd D:\elsa\elsa_store_fe
flutter pub get
```

Các file sinh mã (`*.g.dart`, `*.gr.dart`, `*.freezed.dart`, `injector.config.dart`) đã có sẵn trong repo nên **không cần**
chạy build_runner. Chỉ khi sửa code có `@RoutePage`, `@injectable`, `@RestApi`, `@freezed` mới cần:

```powershell
dart run build_runner build --delete-conflicting-outputs
```

Kiểm tra: `flutter analyze` → `No issues found!` (cho phép vài dòng info), `flutter test` → `All tests passed!`.

---

## Bước 6 — Chạy app

App tự chọn địa chỉ BE (cổng 8080, đường dẫn `/elsa-store/api`):

| Chạy trên | App gọi BE ở |
|---|---|
| Android emulator | `http://10.0.2.2:8080/elsa-store/api` (10.0.2.2 = máy tính từ trong emulator) |
| Điện thoại thật | `http://<IPv4 Wi-Fi của máy tính>:8080/elsa-store/api` (Gradle tự ghi IP vào `lib/core/config/dev_host.dart` mỗi lần build) |
| Web (Chrome) | `http://localhost:8080/elsa-store/api` |

Muốn trỏ đi chỗ khác thì thêm `--dart-define=API_BASE_URL=<url đầy đủ>` vào mọi lệnh `flutter run` / `flutter build` dưới đây.

### 6.1 Android emulator

```powershell
flutter emulators                         # liệt kê máy ảo đã tạo ở 1.5
flutter emulators --launch <tên máy ảo>   # bật máy ảo, đợi tới màn hình chính
flutter devices                           # phải thấy emulator-5554
cd D:\elsa\elsa_store_fe
flutter run -d emulator-5554
```

Lần đầu build Android mất 5–10 phút (Gradle tải Gradle 8.14.3, AGP 8.13.2, Kotlin 2.3.20). Kiểm tra: app mở lên trang chủ,
vào **Tài khoản → Đăng nhập** bằng `admin123` / `admin123` được.

### 6.2 Điện thoại Android thật (tuỳ chọn)

1. Điện thoại bật **Tuỳ chọn nhà phát triển → Gỡ lỗi USB**, cắm cáp, chấp nhận máy tính trên điện thoại.
2. Điện thoại và máy tính dùng **cùng mạng Wi-Fi**.
3. Mở cổng 8080 trên tường lửa Windows (PowerShell **Run as Administrator**):

   ```powershell
   New-NetFirewallRule -DisplayName "Elsa BE 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
   ```

4. `flutter devices` thấy điện thoại → `flutter run -d <id điện thoại>`.

Địa chỉ BE app đang dùng hiện ở **Cài đặt → Máy chủ** trong app và trong log (`BE: ...`). Nếu IP sai (đổi Wi-Fi), build lại.
Muốn có link trong email / trang VNPay trả về đúng thì đặt thêm `setx APP_PUBLIC_URL "http://<IPv4>:8080/elsa-store/api"`
cho BE rồi chạy lại BE.

### 6.3 Web (Chrome)

BE chưa bật CORS ở tầng bảo mật, nên khi chạy web ở máy cá nhân dùng Chrome tắt kiểm tra CORS (chỉ dùng để phát triển):

```powershell
cd D:\elsa\elsa_store_fe
flutter run -d chrome --web-browser-flag "--disable-web-security" --web-browser-flag "--user-data-dir=C:\temp\elsa-chrome"
```

Kiểm tra: Chrome mở app, đăng nhập `admin123` / `admin123` được, trang chủ tải danh sách sản phẩm (CSDL mới thì trống).

Muốn bản web chạy như thật (không cần cờ Chrome): `flutter build web` rồi phục vụ thư mục `build/web` **cùng tên miền** với
BE qua một reverse proxy (Nginx) chuyển `/elsa-store/api/*` (kể cả WebSocket `/elsa-store/api/ws/chat`) về BE — xem
`docs/HUONG_DAN_CAI_DAT.md`, mục cấu hình public.

---

## Bước 7 — Tạo dữ liệu để dùng thử

CSDL mới chỉ có tài khoản quản trị. Thử một vòng nghiệp vụ trong app:

1. **Mở cửa hàng:** đăng xuất → màn đăng nhập → **Mở cửa hàng** → điền hồ sơ (tên đăng nhập / mật khẩu cho chủ cửa hàng).
2. **Duyệt:** đăng nhập `admin123` → **Tổng quan** → hồ sơ chờ duyệt → **Duyệt**.
3. **Chủ cửa hàng:** đăng nhập tài khoản vừa tạo → **Thêm → Danh mục sản phẩm** tạo danh mục cha (vd. "Áo sơ mi") và danh mục
   con (vd. "Sơ mi ngắn tay") → tab **Sản phẩm** thêm sản phẩm + phân loại (màu / cỡ / giá) → **Kho hàng** nhập tồn kho.
4. **Khách:** đăng xuất → **Tạo tài khoản** → mua sản phẩm (COD) → chủ / nhân viên xác nhận, giao → khách bấm "Đã nhận hàng".

Hoặc chạy bộ Postman tự tạo dữ liệu mẫu (cần Python 3: `winget install Python.Python.3.12`):

```powershell
cd D:\elsa\elsa_store
python -m pip install requests
python postman\build_collection.py postman\elsa_store.postman_collection.json --run http://localhost:8080/elsa-store/api
```

Kết quả cuối phải là `FAILED: 0 / ...`. Mỗi lần chạy tạo một cửa hàng "Elsa Demo …", sản phẩm, khách, đơn hàng mới.

---

## Bước 8 — Bảng kiểm tra cuối (AI tự đánh dấu)

- [ ] `java -version` = 21, `flutter --version` = 3.47.8, `flutter doctor` không còn ✗ ở Flutter / Android / Chrome.
- [ ] MySQL chạy, có schema `elsa_store_db`.
- [ ] BE chạy, `POST /auth/login` với `admin123` trả `accessToken`.
- [ ] `flutter test` trong `elsa_store_fe` → `All tests passed!`.
- [ ] App chạy trên emulator, đăng nhập `admin123` được.
- [ ] App chạy trên Chrome, đăng nhập `admin123` được.
- [ ] (Tuỳ chọn) bộ Postman `FAILED: 0`.

---

## Lỗi thường gặp

| Hiện tượng | Cách sửa |
|---|---|
| `git clone -b nang-cap` báo không có nhánh | Ở máy cũ chưa `git push -u origin nang-cap` (Bước 0) |
| BE: `Access denied for user 'root'` | Sai `DB_PASSWORD`; đặt lại bằng `setx` rồi **mở PowerShell mới** |
| BE: `Communications link failure` | MySQL chưa chạy: `Get-Service MySQL*` → `Start-Service MySQL80` |
| `mvnw.cmd` báo không thấy Java / `JAVA_HOME` | Đặt `JAVA_HOME` trỏ tới JDK 21 (xem 1.2) |
| `flutter run` lỗi phiên bản Gradle / AGP / Kotlin | Đang dùng sai bản Flutter — phải đúng 3.47.8 (`flutter --version`) |
| Build Android lỗi `Android license status unknown` | `flutter doctor --android-licenses` |
| Emulator: app báo mất kết nối / không tải được | BE chưa chạy, hoặc BE không ở cổng 8080 (dùng `--dart-define=API_BASE_URL=http://10.0.2.2:<cổng>/elsa-store/api`) |
| Điện thoại thật không gọi được BE | Khác Wi-Fi, tường lửa chặn 8080 (6.2), hoặc IP đổi → build lại |
| Web báo lỗi CORS / `XMLHttpRequest error` | Chạy đúng lệnh 6.3 có `--disable-web-security` |
| Không có thông báo đẩy (push) | Bình thường khi thiếu file Firebase — app vẫn có thông báo trong app |
| Nút AI không hiện | Chưa đặt `GEMINI_API_KEY` (tuỳ chọn) |
| Quên mật khẩu không nhận email | Chưa cấu hình mail — mã OTP in trong log của BE |

Tài liệu chi tiết hơn: `elsa_store/README.md` (BE, mọi API và biến môi trường), `elsa_store/docs/HUONG_DAN_CAI_DAT.md`
(cấu hình local / public, khoá API), `elsa_store/docs/TINH_NANG.md` (danh sách chức năng), `elsa_store_fe/README.md` (app).
