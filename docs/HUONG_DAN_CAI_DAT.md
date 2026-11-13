# Hướng dẫn cài đặt và cấu hình Elsa Store

Tài liệu gồm cả hai phần: **BE** (Spring Boot, repo `elsa_store`) và **FE** (Flutter, repo `elsa_store_fe`). Có hai cách
chạy:

- **Local:** chạy trên máy cá nhân để phát triển và demo trong mạng Wi-Fi.
- **Public:** đưa lên máy chủ có tên miền và HTTPS.

Mục lục:

1. [Yêu cầu phần mềm](#1-yêu-cầu-phần-mềm)
2. [Các dịch vụ / API bên ngoài và khoá cần có](#2-các-dịch-vụ--api-bên-ngoài-và-khoá-cần-có)
3. [Bảng biến môi trường BE — khoá điền vào đâu](#3-bảng-biến-môi-trường-be--khoá-điền-vào-đâu)
4. [Cấu hình LOCAL](#4-cấu-hình-local)
5. [Cấu hình PUBLIC (triển khai thật)](#5-cấu-hình-public-triển-khai-thật)
6. [Danh sách API của hệ thống](#6-danh-sách-api-của-hệ-thống)
7. [Kiểm tra sau khi cài và lỗi thường gặp](#7-kiểm-tra-sau-khi-cài-và-lỗi-thường-gặp)

---

## 1. Yêu cầu phần mềm

| Phần mềm | Phiên bản | Dùng cho |
|---|---|---|
| JDK | 21 | Chạy / build BE |
| Maven | 3.9+ (hoặc `./mvnw` có sẵn trong repo) | Build BE |
| MySQL | 8.x | Cơ sở dữ liệu |
| Flutter | 3.47 (Dart 3.13) | Build app |
| Android Studio + Android SDK | bản mới, có emulator | Chạy app Android |
| JDK cho Gradle Android | 17 trở lên | Build APK |
| Docker Desktop *(tuỳ chọn)* | bản mới | Chạy MySQL + BE bằng một lệnh, triển khai máy chủ |
| Postman *(tuỳ chọn)* | bản mới | Thử API |

Tài khoản quản trị tạo sẵn khi BE khởi động lần đầu: **`admin123` / `admin123`**. Khi chạy public phải đổi mật khẩu
này ngay.

---

## 2. Các dịch vụ / API bên ngoài và khoá cần có

Chỉ **MySQL** là bắt buộc. Thiếu các dịch vụ còn lại thì BE vẫn chạy, chỉ tắt tính năng tương ứng.

| Dịch vụ | Bắt buộc? | Thiếu thì sao | Lấy khoá ở đâu | Điền vào |
|---|---|---|---|---|
| **MySQL** | Có | BE không khởi động | Tự cài, hoặc dùng container `elsa-db` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| **JWT signer key** | Có khi public | Dùng khoá phát triển công khai trong `application.yaml` → **không an toàn** | Tự sinh chuỗi ngẫu nhiên ≥ 64 ký tự | `JWT_SIGNER_KEY` |
| **VNPay** (thanh toán online) | Không | Dùng tài khoản **sandbox** có sẵn (chỉ thử, không mất tiền) | sandbox: <https://sandbox.vnpayment.vn/devreg/>; thật: ký hợp đồng với VNPay | `VNPAY_TMN_CODE`, `VNPAY_SECRET_KEY`, `VNPAY_URL` |
| **Gmail SMTP** (email đơn hàng, mã OTP quên mật khẩu) | Không | Email chỉ ghi ra log; mã OTP xem trong log BE | Google Account → Bảo mật → bật xác minh 2 bước → **Mật khẩu ứng dụng** (16 ký tự) | `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` |
| **Firebase Cloud Messaging** (push) | Không | Chỉ có thông báo trong app, không có push | Firebase console → Project settings | BE: `FIREBASE_CREDENTIALS`; FE: `android/app/google-services.json` |
| **Google Gemini** (trợ lý AI) | Không | Ẩn các nút AI của chat / trợ lý mua sắm. *Nhận xét báo cáo* và *viết mô tả* vẫn chạy bằng quy tắc / mẫu soạn sẵn | <https://aistudio.google.com/apikey> (có gói miễn phí) | `GEMINI_API_KEY` |
| Claude / máy chủ chuẩn OpenAI *(thay Gemini)* | Không | — | console.anthropic.com, Groq, OpenRouter, Ollama… | `ANTHROPIC_API_KEY` hoặc `AI_PROVIDER=openai` + `AI_API_KEY` + `AI_BASE_URL` + `AI_ASSISTANT_MODEL` |

> **Quan trọng — không commit khoá:** file `.env`, file service account Firebase (`secrets/*.json`),
> `google-services.json` và `GoogleService-Info.plist` đều đã nằm trong `.gitignore`. Mỗi người chạy dự án tự tải file
> của project Firebase riêng. Không dán khoá thật vào `application.yaml`.

### 2.1 Lấy từng khoá

**JWT_SIGNER_KEY** — sinh chuỗi ngẫu nhiên (chạy một trong hai lệnh):

```bash
openssl rand -base64 64
```

```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

Đổi khoá thì mọi phiên đăng nhập cũ hết hiệu lực.

**Gmail App Password**

1. Vào <https://myaccount.google.com/security>, bật **Xác minh 2 bước**.
2. Mở **Mật khẩu ứng dụng** (App passwords), tạo mật khẩu tên "Elsa Store" và chép chuỗi 16 ký tự.
3. Điền như sau:
   - `MAIL_USERNAME` = địa chỉ Gmail.
   - `MAIL_PASSWORD` = chuỗi 16 ký tự (bỏ dấu cách).
   - `MAIL_FROM` = để trống, hoặc ghi cùng địa chỉ Gmail.

**VNPay sandbox**

- Mặc định BE đã dùng một tài khoản sandbox công khai, chạy được ngay.
- Muốn có tài khoản sandbox riêng: đăng ký ở <https://sandbox.vnpayment.vn/devreg/>. Email trả về có **vnp_TmnCode** →
  điền `VNPAY_TMN_CODE`, và **vnp_HashSecret** → điền `VNPAY_SECRET_KEY`.
- Thẻ thử: NCB `9704198526191432198`, chủ thẻ `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.
- Khi chạy thật: đổi `VNPAY_URL` sang URL production VNPay cấp (`https://pay.vnpay.vn/vpcpay.html`). Khai báo **IPN URL**
  `https://<tên-miền>/elsa-store/api/payment/vnpay-ipn` trong trang quản trị VNPay.

**Firebase (push)**

1. Tạo project ở <https://console.firebase.google.com>.
2. **Phía BE:**
   1. Mở *Project settings → Service accounts → Generate new private key* để tải file JSON.
   2. Đặt file vào thư mục `secrets/` của repo BE (đã bị git bỏ qua).
   3. Đặt `FIREBASE_CREDENTIALS` thành đường dẫn tới file:
      - Chạy local: đường dẫn tuyệt đối, ví dụ `D:/elsa/secrets/firebase-service-account.json`.
      - Chạy Docker: `/app/secrets/<tên-file>.json`.
3. **Phía FE (Android):**
   1. Trong cùng project, *Add app → Android*, package name **`com.example.flutter_skeleton`** (xem `applicationId`
      trong `android/app/build.gradle.kts`).
   2. Tải `google-services.json` đặt vào `elsa_store_fe/android/app/`. Plugin Google Services chỉ bật khi file này tồn
      tại.
   3. Có thể thay file đó bằng dart-define: `--dart-define=FIREBASE_API_KEY=… --dart-define=FIREBASE_APP_ID=…
      --dart-define=FIREBASE_MESSAGING_SENDER_ID=… --dart-define=FIREBASE_PROJECT_ID=…`.
4. **iOS:** thêm `GoogleService-Info.plist` vào `ios/Runner/` và bật *Push Notifications* trong Xcode.
5. Web không nhận push, chỉ có thông báo trong app.

**Gemini**

1. Vào <https://aistudio.google.com/apikey>, chọn *Create API key* rồi chép khoá.
2. Đặt `GEMINI_API_KEY`. BE tự chọn provider `gemini`, model mặc định `gemini-3.8-flash`; khi model chính quá tải BE tự
   chuyển sang `gemini-3.5-flash`, rồi `gemini-2.5-flash`.
3. Muốn đổi model: đặt `AI_ASSISTANT_MODEL` và `AI_ASSISTANT_FALLBACK_MODELS` (cách nhau bằng dấu phẩy, `none` = tắt).
4. App **không bao giờ** giữ khoá AI; mọi lời gọi AI đi qua BE.

---

## 3. Bảng biến môi trường BE — khoá điền vào đâu

BE đọc cấu hình từ `src/main/resources/application.yaml`. Mỗi giá trị có dạng `${TÊN_BIẾN:mặc-định}`, nên **chỉ cần đặt
biến môi trường, không sửa file yaml**. Có ba nơi đặt biến, tuỳ cách chạy:

| Cách chạy BE | Đặt biến ở đâu |
|---|---|
| IntelliJ IDEA | *Run → Edit Configurations → ElsaStoreApplication → Environment variables* (dán dạng `KEY=value;KEY2=value2`) |
| Dòng lệnh (`mvn spring-boot:run` / `java -jar`) | Biến môi trường của shell (xem ví dụ ở mục 4.1) |
| Docker Compose | File `.env` cạnh `docker-compose.yml` (chép từ `.env.example`) |

> Spring Boot **không tự đọc file `.env`**. File `.env` chỉ có tác dụng khi chạy bằng `docker compose`.

| Nhóm | Biến | Mặc định | Local | Public |
|---|---|---|---|---|
| CSDL | `DB_URL` | `jdbc:mysql://localhost:3306/elsa_store_db` | giữ nguyên | do compose đặt / URL MySQL máy chủ |
| | `DB_USERNAME` / `DB_PASSWORD` | `root` / giá trị trong `application.yaml` | tài khoản MySQL máy bạn | tài khoản riêng, mật khẩu mạnh |
| Bảo mật | `JWT_SIGNER_KEY` | khoá phát triển | có thể bỏ trống | **bắt buộc**, ≥ 64 ký tự ngẫu nhiên |
| | `JWT_VALID_SECONDS` / `JWT_REFRESH_SECONDS` | `3600` / `604800` | — | tuỳ chọn |
| Địa chỉ | `APP_PUBLIC_URL` | `http://localhost:8080/elsa-store/api` | `http://<IP-LAN>:8080/elsa-store/api` khi dùng điện thoại thật | `https://<tên-miền>/elsa-store/api` |
| VNPay | `VNPAY_TMN_CODE` / `VNPAY_SECRET_KEY` | sandbox | bỏ trống | mã thật |
| | `VNPAY_URL` | sandbox | bỏ trống | URL production |
| | `VNPAY_RETURN_URL` | `{APP_PUBLIC_URL}/payment/vn-pay-callback` | bỏ trống | bỏ trống (tự theo `APP_PUBLIC_URL`) |
| Email | `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | trống | tuỳ chọn | nên có |
| | `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` | — | đổi nếu dùng SMTP khác |
| Push | `FIREBASE_CREDENTIALS` | trống | đường dẫn file JSON | `/app/secrets/<file>.json` |
| AI | `GEMINI_API_KEY` | trống | tuỳ chọn | tuỳ chọn |
| | `AI_PROVIDER`, `AI_API_KEY`, `ANTHROPIC_API_KEY`, `AI_BASE_URL`, `AI_ASSISTANT_MODEL`, `AI_ASSISTANT_FALLBACK_MODELS`, `AI_TIMEOUT_SECONDS`, `AI_AUTO_REPLY_DELAY_SECONDS` | xem README BE | — | — |
| Upload | `UPLOAD_MAX_FILE_SIZE` / `UPLOAD_MAX_REQUEST_SIZE` | `5MB` / `30MB` | — | khớp với `client_max_body_size` của Nginx |
| Nghiệp vụ | `ORDER_UNPAID_CANCEL_MINUTES`, `ORDER_AUTO_COMPLETE_DAYS`, `ORDER_RETURN_WINDOW_DAYS`, `CART_REMINDER_HOURS`, `COINS_*`, `REFERRAL_*` | xem README BE, mục *Biến môi trường* | — | — |

---

## 4. Cấu hình LOCAL

### 4.1 Cách A — chạy BE trực tiếp (IntelliJ hoặc dòng lệnh)

1. **Tạo CSDL** (Hibernate tự tạo bảng với `ddl-auto=update`, chỉ cần schema trống):

   ```sql
   CREATE DATABASE elsa_store_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. **Đặt biến môi trường.** Tối thiểu cần `DB_USERNAME` và `DB_PASSWORD` nếu khác mặc định. Ví dụ trên PowerShell:

   ```powershell
   $env:DB_USERNAME = "root"
   $env:DB_PASSWORD = "<mật-khẩu-mysql>"
   $env:GEMINI_API_KEY = "<khoá-gemini>"          # tuỳ chọn
   $env:MAIL_USERNAME = "<gmail>"                 # tuỳ chọn
   $env:MAIL_PASSWORD = "<app-password-16-ký-tự>" # tuỳ chọn
   $env:FIREBASE_CREDENTIALS = "D:/elsa/secrets/firebase-service-account.json" # tuỳ chọn
   ```

   Trong IntelliJ thì dán các cặp trên vào ô *Environment variables* của cấu hình chạy.

3. **Chạy BE:**

   ```bash
   mvn spring-boot:run -Dspotless.apply.skip=true
   ```

   BE chạy ở `http://localhost:8080/elsa-store/api`. Cờ `-Dspotless.apply.skip=true` giúp Maven không định dạng lại
   toàn bộ mã nguồn mỗi lần chạy.

### 4.2 Cách B — chạy bằng Docker (không cần cài Java / MySQL)

```bash
cp .env.example .env     # mở .env điền khoá; để trống = mặc định
docker compose up -d --build
```

- MySQL nằm trong container `elsa-db`, mở ra máy ở cổng **3307** để không đụng MySQL sẵn có ở 3306.
- BE nằm trong container `elsa-app`, chạy ở `http://localhost:8080/elsa-store/api`.
- Ảnh tải lên lưu ở `./uploads`. File Firebase đặt ở `./secrets/` rồi ghi
  `FIREBASE_CREDENTIALS=/app/secrets/<file>.json` trong `.env`.
- Xem log bằng `docker compose logs -f elsa-app`. Dừng bằng `docker compose down`; thêm `-v` sẽ **xoá dữ liệu** MySQL.

### 4.3 Chạy app Flutter (local)

```bash
cd elsa_store_fe
flutter pub get
dart run build_runner build --delete-conflicting-outputs
flutter run
```

App **tự chọn địa chỉ BE** (`lib/core/config/app_config.dart`). Không cần sửa code:

| Chạy trên | Địa chỉ BE app dùng |
|---|---|
| Android emulator | `http://10.0.2.2:8080/elsa-store/api` |
| Điện thoại thật | `http://<IPv4 Wi-Fi của laptop>:8080/elsa-store/api`, IP tự ghi vào `lib/core/config/dev_host.dart` mỗi lần build Android (task Gradle `writeDevHost`) |
| Web, desktop, iOS simulator | `http://localhost:8080/elsa-store/api` |

Muốn trỏ tới địa chỉ khác thì truyền URL đầy đủ. URL này ghi đè mọi trường hợp trên:

```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.10:8080/elsa-store/api
```

**Khi dùng điện thoại thật trong mạng LAN:**

1. Điện thoại và laptop dùng **cùng mạng Wi-Fi**.
2. Mở cổng 8080 trên Windows Firewall (PowerShell chạy quyền Admin):

   ```powershell
   New-NetFirewallRule -DisplayName "Elsa BE 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
   ```

3. Đặt `APP_PUBLIC_URL=http://<IPv4-laptop>:8080/elsa-store/api` cho BE. Khi đó link trong email và trang VNPay trả về
   mở được từ điện thoại.
4. Địa chỉ app đang dùng hiện ở **Cài đặt → Máy chủ** và trong log (`BE: …`).

**Chạy app bản web:**

- BE chưa bật CORS, nên phục vụ `build/web` **cùng origin** với BE, hoặc dùng proxy chuyển `/elsa-store/api/*`.
- Proxy phải chuyển cả WebSocket `/elsa-store/api/ws/chat` cho chat thời gian thực.

### 4.4 Thanh toán VNPay khi chạy local

- Sandbox chạy được ngay: app mở trang VNPay trong WebView, rồi bắt địa chỉ trả về `/payment/vn-pay-callback` và gọi BE
  kiểm chữ ký.
- **IPN** (máy chủ VNPay gọi BE) không tới được `localhost`. Đơn vẫn được cập nhật qua callback trong app.
- Muốn thử cả IPN thì mở đường hầm công khai tạm thời, rồi đặt `APP_PUBLIC_URL` = địa chỉ đường hầm +
  `/elsa-store/api`:

  ```bash
  cloudflared tunnel --url http://localhost:8080
  ```

  hoặc

  ```bash
  ngrok http 8080
  ```

### 4.5 Thử API bằng Postman (local)

1. Import `postman/elsa_store.postman_collection.json`.
2. Kiểm tra biến `baseUrl` = `http://localhost:8080/elsa-store/api`.
3. *Run collection*. Bộ request tự đi hết một vòng nghiệp vụ (ADMIN duyệt cửa hàng → tạo sản phẩm → khách đặt hàng →
   báo cáo…).

Cũng có thể chạy cả bộ bằng dòng lệnh:

```bash
python postman/build_collection.py postman/elsa_store.postman_collection.json --run http://localhost:8080/elsa-store/api
```

---

## 5. Cấu hình PUBLIC (triển khai thật)

Mô hình khuyến nghị: **một VPS Linux** (Ubuntu 22.04+, 2 GB RAM trở lên) + **tên miền** + **Nginx** làm reverse proxy có
HTTPS. BE và MySQL chạy bằng Docker Compose.

```
Điện thoại / trình duyệt ──HTTPS──> Nginx (443) ──HTTP──> elsa-app:8080 ──> elsa-db:3306
                                     │                      (Docker, chỉ mở cho máy nội bộ)
VNPay IPN ───────────────────────────┘
```

### 5.1 Chuẩn bị máy chủ

```bash
# Cài Docker + plugin compose
curl -fsSL https://get.docker.com | sh
# Lấy mã nguồn BE
git clone <repo-BE> elsa_store && cd elsa_store
mkdir -p secrets uploads
```

Trỏ bản ghi DNS **A** của tên miền (ví dụ `api.elsastore.vn`) về IP của VPS.

### 5.2 File `.env` cho máy chủ

```dotenv
MYSQL_DATABASE=elsa_store_db
MYSQL_USER=elsa_user
MYSQL_PASSWORD=<mật-khẩu-mạnh>
MYSQL_ROOT_PASSWORD=<mật-khẩu-root-mạnh>
MYSQL_HOST_PORT=127.0.0.1:3307      # chỉ mở MySQL cho chính máy chủ, không lộ ra Internet
APP_HOST_PORT=127.0.0.1:8080        # BE chỉ nhận từ Nginx

JWT_SIGNER_KEY=<chuỗi-ngẫu-nhiên-≥64-ký-tự>
APP_PUBLIC_URL=https://api.elsastore.vn/elsa-store/api

VNPAY_TMN_CODE=<mã-thật>
VNPAY_SECRET_KEY=<secret-thật>
VNPAY_URL=https://pay.vnpay.vn/vpcpay.html

MAIL_USERNAME=<gmail>
MAIL_PASSWORD=<app-password>
MAIL_FROM=<gmail>

FIREBASE_CREDENTIALS=/app/secrets/firebase-service-account.json
GEMINI_API_KEY=<khoá-gemini>
```

- Chép file service account Firebase vào `./secrets/firebase-service-account.json` (dùng `scp`, **không** commit).
- Chạy BE và MySQL:

  ```bash
  docker compose up -d --build
  ```

### 5.3 Nginx + HTTPS

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

Tạo `/etc/nginx/sites-available/elsa`:

```nginx
server {
    server_name api.elsastore.vn;

    # Khớp UPLOAD_MAX_REQUEST_SIZE (30MB) để tải được bộ ảnh / tệp Excel
    client_max_body_size 30m;

    location /elsa-store/api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;          # gọi AI có thể mất vài chục giây

        # WebSocket cho chat thời gian thực (/elsa-store/api/ws/chat)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/elsa /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.elsastore.vn      # cấp và tự gia hạn chứng chỉ HTTPS
sudo ufw allow OpenSSH && sudo ufw allow 'Nginx Full' && sudo ufw enable
```

### 5.4 Khai báo với dịch vụ bên ngoài

| Dịch vụ | Việc cần làm |
|---|---|
| VNPay | Trong trang quản trị merchant khai báo **IPN URL** `https://api.elsastore.vn/elsa-store/api/payment/vnpay-ipn`. Return URL BE tự tạo theo `APP_PUBLIC_URL` |
| Gmail | Nếu gửi nhiều nên dùng SMTP dịch vụ (SendGrid, Mailgun…): đổi `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` |
| Firebase | Dùng cùng project với file `google-services.json` đã đóng vào app |
| Gemini | Theo dõi hạn mức ở AI Studio; gói miễn phí có giới hạn lượt/phút — BE tự thử model dự phòng khi hết lượt |

### 5.5 Build app trỏ tới máy chủ public

```bash
cd elsa_store_fe
flutter build apk --release --dart-define=API_BASE_URL=https://api.elsastore.vn/elsa-store/api
# hoặc gói cho Google Play:
flutter build appbundle --release --dart-define=API_BASE_URL=https://api.elsastore.vn/elsa-store/api
```

- File APK nằm ở `build/app/outputs/flutter-apk/app-release.apk`.
- Phát hành lên Google Play thì phải ký bằng keystore riêng: tạo `android/key.properties` và khai báo `signingConfigs` (xem
  hướng dẫn *Build and release an Android app* của Flutter); không commit keystore.
- Nên đổi `applicationId` khỏi `com.example.flutter_skeleton` trước khi lên Play. Nếu đổi thì phải thêm lại app Android
  trong Firebase với package mới và tải lại `google-services.json`.
- Khi chỉ dùng HTTPS có thể bỏ `android:usesCleartextTraffic="true"` trong `AndroidManifest.xml`. Dòng này hiện để
  dành cho chạy HTTP trong LAN.
- **Bản web:** `flutter build web --dart-define=API_BASE_URL=https://api.elsastore.vn/elsa-store/api`, rồi để Nginx
  phục vụ `build/web` ở **cùng tên miền** (thêm `location / { root /var/www/elsa; try_files $uri /index.html; }`) để
  khỏi cần CORS.

### 5.6 Việc bắt buộc sau khi lên public

- [ ] Đăng nhập `admin123` / `admin123` và **đổi mật khẩu ngay** (Cài đặt → Đổi mật khẩu).
- [ ] Đã đặt `JWT_SIGNER_KEY` riêng (BE ghi cảnh báo trong log nếu vẫn dùng khoá mặc định).
- [ ] MySQL và cổng 8080 **không** mở ra Internet (chỉ `127.0.0.1`); chỉ mở 80/443 và SSH.
- [ ] Sao lưu CSDL định kỳ:

  ```bash
  docker exec elsa-db mysqldump -uroot -p<root-pass> elsa_store_db > backup_$(date +%F).sql
  ```

- [ ] Sao lưu thư mục `uploads/` (ảnh sản phẩm, ảnh đánh giá, ảnh chat, logo).
- [ ] Đổi sang thông tin VNPay production và đã khai báo IPN URL.

Cập nhật phiên bản mới:

```bash
git pull
docker compose up -d --build
```

Hibernate tự thêm bảng và cột mới. Các cột mới đều cho phép để trống nên dữ liệu cũ giữ nguyên.

---

## 6. Danh sách API của hệ thống

- Tất cả API nằm dưới **`{base}/elsa-store/api`**. Local: `http://localhost:8080/elsa-store/api`; public:
  `https://<tên-miền>/elsa-store/api`.
- Xác thực bằng header `Authorization: Bearer <accessToken>`. Lấy token từ `POST /auth/login`; làm mới bằng
  `POST /auth/refreshToken`.
- Danh sách đầy đủ 239 endpoint ở [`docs/TINH_NANG.md`](TINH_NANG.md) (phụ lục). Request mẫu có sẵn body ở bộ Postman
  `postman/elsa_store.postman_collection.json`.

Các nhóm chính:

| Nhóm | Đường dẫn | Ghi chú |
|---|---|---|
| Xác thực, mật khẩu | `/auth/*`, `/auth/password/forgot\|verify\|reset` | OTP gửi qua email (hoặc log khi chưa cấu hình mail) |
| Người dùng, địa chỉ, khách hàng | `/users/*`, `/addresses/*`, `/customers/*` | |
| Mở cửa hàng, duyệt | `/store-registrations/*` | ADMIN duyệt / đình chỉ |
| Cửa hàng, nhân sự | `/stores/*`, `/store-members/*` | |
| Sản phẩm, phân loại, ảnh | `/products/*`, `/product-variants/*`, `/products/import` | Nhập Excel |
| Danh mục | `/categories/*` | |
| Kho, nhà cung cấp, phiếu nhập | `/inventories/*`, `/suppliers/*`, `/purchase-orders/*` | |
| Giỏ hàng, đặt hàng | `/cart/*`, `/orders/*` | |
| Thanh toán VNPay | `/payment/*` | callback `/payment/vn-pay-callback`, IPN `/payment/vnpay-ipn` |
| Khuyến mãi, mã giảm giá | `/promotions/*` | |
| Đánh giá, hỏi đáp, yêu thích | `/products/{productId}/reviews`, `/products/{productId}/questions`, `/reviews/*`, `/questions/*`, `/wishlist/*` | |
| Xu, điểm danh, giới thiệu | `/coins/*`, `/referrals/*` | |
| Đổi / trả hàng | `/returns/*` | |
| Báo cáo | `/reports/*` | dashboard, doanh thu, giờ vàng, mục tiêu tháng, hàng chậm bán, xuất Excel |
| Thông báo | `/notifications/*` | đăng ký token FCM: `POST /notifications/devices` |
| Chat | `/conversations/*`, `/chat/quick-replies`, WebSocket `/ws/chat?token=<accessToken>` | |
| AI | `/ai/status`, `/ai/ask`, `/ai/conversations/{id}/suggest-reply`, `/ai/report-insights`, `/ai/product-description`, `/ai/reviews/{id}/suggest-reply`, `/ai/questions/{id}/suggest-answer`, `/ai/products/{productId}/review-digest`, `/ai/store-settings` | cần `GEMINI_API_KEY`; nhận xét báo cáo, viết mô tả và soạn phản hồi / trả lời vẫn chạy bằng mẫu soạn sẵn khi không có khoá |
| Trang chủ, tìm kiếm | `/home/*`, `/search/*`, `/banners/*` | |
| Liên kết mở app | `/app-links/*` | deep link `elsastore://…` |

---

## 7. Kiểm tra sau khi cài và lỗi thường gặp

**Kiểm tra nhanh**

```bash
# BE sống và AI đã bật chưa
curl http://localhost:8080/elsa-store/api/ai/status
# → {"code":1000,"data":{"enabled":true,"provider":"gemini"}}  (enabled=false nghĩa là chưa có khoá AI)

# Đăng nhập admin
curl -X POST http://localhost:8080/elsa-store/api/auth/login \
     -H "Content-Type: application/json" -d '{"username":"admin123","password":"admin123"}'
```

Khi khởi động, log BE còn cho biết:

- Có khoá AI chưa: `AI features on: provider=…` hoặc `No AI API key …`.
- Firebase có được bật không.
- Còn dùng khoá JWT mặc định hay không.

**Lỗi thường gặp**

| Hiện tượng | Nguyên nhân / cách sửa |
|---|---|
| BE báo `Access denied for user` / `Communications link failure` | Sai `DB_USERNAME` / `DB_PASSWORD`, hoặc MySQL chưa chạy / sai cổng (Docker dùng 3307 ra máy) |
| Điện thoại thật báo mất kết nối | Khác mạng Wi-Fi, Firewall chặn 8080, hoặc IP laptop đổi → build lại app (task `writeDevHost` ghi IP mới) |
| Emulator không gọi được BE | Emulator phải dùng `10.0.2.2`, không dùng `localhost` |
| App web báo lỗi CORS | Phục vụ web cùng origin với BE hoặc qua proxy (mục 4.3) |
| Không nhận email / OTP | Chưa đặt `MAIL_USERNAME` / `MAIL_PASSWORD` (xem mã OTP trong log BE), hoặc dùng mật khẩu Gmail thường thay vì App Password |
| Không có push | Thiếu `FIREBASE_CREDENTIALS` (BE) hoặc `google-services.json` (FE); package name trong Firebase phải trùng `applicationId`. Máy Xiaomi/Oppo/Vivo cần bật *Tự khởi chạy* (màn **Nhận thông báo khi tắt app** trong app) |
| Nút AI không hiện / AI báo bận | Chưa có `GEMINI_API_KEY`, hoặc gói miễn phí hết lượt (BE tự thử model dự phòng; nhận xét báo cáo và viết mô tả chuyển sang bản theo quy tắc) |
| Tải ảnh / Excel báo lỗi 413 | Nginx thiếu `client_max_body_size 30m` hoặc ảnh lớn hơn `UPLOAD_MAX_FILE_SIZE` |
| Chat không cập nhật tức thì khi chạy public | Nginx thiếu các dòng `Upgrade` / `Connection` cho WebSocket |
| VNPay báo sai chữ ký | `VNPAY_SECRET_KEY` không khớp `VNPAY_TMN_CODE`, hoặc đang trộn thông tin sandbox với URL production |
| `flutter run` lỗi Gradle phiên bản | Flutter 3.47 cần Gradle ≥ 8.14, AGP ≥ 8.11.1, Kotlin ≥ 2.2.20 (repo đã cấu hình sẵn) |
| build_runner treo | Dùng đúng bộ sinh mã trong `pubspec.yaml`; dừng tiến trình dart, `git checkout` các file `*.g.dart` rồi chạy lại |
