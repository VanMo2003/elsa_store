# elsa_store

Backend Spring Boot 3.5 / Java 21 của ứng dụng bán hàng thời trang Elsa Store (đồ án tốt nghiệp).
API nằm dưới `/elsa-store/api`, dùng MySQL, xác thực JWT, thanh toán VNPay, email (Spring Mail) và
thông báo đẩy (Firebase Cloud Messaging).

```bash
./mvnw spring-boot:run      # cần MySQL local: localhost:3306/elsa_store_db (xem application.yaml)
./mvnw test                 # chạy test
```

Tài khoản quản trị mặc định `admin123` / `admin123` được tạo khi khởi động nếu chưa có.

## Thông báo, push và email

Cả email và push đều **tuỳ chọn**: chưa cấu hình thì BE vẫn chạy, thông báo trong app vẫn có, email
chỉ được ghi log.

| Sự kiện | Ai nhận | Kênh |
|---|---|---|
| Khách đặt đơn (`POST /orders`) | Khách | Trong app + push + email xác nhận |
| Khách đặt đơn | Admin, nhân viên | Trong app + push |
| Đơn chuyển sang đã xác nhận / đã huỷ / hoàn thành | Khách | Trong app + push + email |
| Thanh toán chuyển sang thành công / thất bại (callback VNPay hoặc `PUT /orders/{id}/status?paymentStatus=`) | Khách | Trong app + push + email |

Chỉ báo khi trạng thái **thật sự đổi** (VNPay gọi lại nhiều lần không gửi trùng). Người nhận là tài
khoản của khách hàng gắn với đơn, nếu đơn không có khách hàng thì là người tạo đơn.

- `Notification` là hộp thông báo trong app: `GET /notifications`, `GET /notifications/unread-count`,
  `PUT /notifications/{id}/read`, `PUT /notifications/read-all`. Nội dung nằm trong
  `utils/NotificationMessages`, deep link trong `utils/AppLinks` (`elsastore://orders`,
  `elsastore://notifications`).
- Push đi qua FCM HTTP v1 (`FcmPushNotificationSender`, xác thực bằng service account) tới các
  `DeviceToken` mà app đăng ký bằng `POST /notifications/devices` và gỡ bằng
  `DELETE /notifications/devices?token=` trước khi đăng xuất. Token FCM báo không còn đăng ký thì bị xoá.
- Email do `MailServiceImpl` gửi (HTML + bản văn bản thường). Cả push lẫn email chạy `@Async` sau khi
  giao dịch commit (`@TransactionalEventListener(AFTER_COMMIT)`), nên đơn bị rollback không thông báo
  cho ai và độ trễ SMTP/FCM không làm chậm request.
- Ứng dụng mail bỏ qua liên kết custom-scheme, nên nút "Mở ứng dụng" trong email trỏ tới trang công
  khai `GET /app-links/{target}` (dựng từ `APP_PUBLIC_URL`), trang đó chuyển điện thoại sang
  `elsastore://{target}`. Chỉ các đích trong danh sách cho phép (`orders`, `notifications`) được phục vụ.

**Email (Gmail):**

1. Bật xác minh 2 bước cho tài khoản Google, vào *Bảo mật → Mật khẩu ứng dụng* tạo App Password.
2. Đặt `MAIL_USERNAME` = địa chỉ Gmail, `MAIL_PASSWORD` = App Password 16 ký tự (không dấu cách).
3. Đặt `APP_PUBLIC_URL` là địa chỉ API mà **điện thoại mở được** (ví dụ
   `http://192.168.1.10:8080/elsa-store/api` khi chạy trong mạng LAN).

Thử mail ở máy local mà không cần Gmail: trỏ `MAIL_HOST`/`MAIL_PORT` tới một SMTP giả (MailHog,
smtp4dev…) với `MAIL_SMTP_AUTH=false`, `MAIL_SMTP_STARTTLS=false` và `MAIL_USERNAME` bất kỳ. Email đầu
tiên sau khi khởi động có thể mất ~10 giây (JavaMail phân giải tên máy một lần).

**Thông báo đẩy (Firebase Cloud Messaging):**

1. Tạo project trên [Firebase console](https://console.firebase.google.com), thêm app Android với
   package `com.example.flutter_skeleton` và tải `google-services.json` cho app Flutter (xem README FE).
2. *Project settings → Service accounts → Generate new private key*, lưu file JSON ở nơi **không
   commit** (`.gitignore` đã chặn `secrets/` và `*service-account*.json`).
3. Đặt `FIREBASE_CREDENTIALS` = đường dẫn tới file đó. Log khởi động không còn dòng
   `FIREBASE_CREDENTIALS is not set` là đã bật.

## Biến môi trường

Đặt trong cấu hình chạy của IDE hoặc biến môi trường hệ điều hành (Spring Boot không tự đọc file `.env`).

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` | Máy chủ SMTP |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | trống | Tài khoản SMTP; trống thì chỉ ghi log email |
| `MAIL_FROM` | trống (dùng `MAIL_USERNAME`) | Địa chỉ người gửi |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `true` / `true` | Tắt khi thử với SMTP giả trên máy |
| `APP_PUBLIC_URL` | `http://localhost:8080/elsa-store/api` | Địa chỉ API công khai cho link trong email |
| `FIREBASE_CREDENTIALS` | trống | Đường dẫn file JSON service account Firebase; trống thì tắt push |

Bảng `notifications` và `device_tokens` được Hibernate tạo tự động (`ddl-auto=update`).

## Ghi chú

- Format code tự động chạy qua Spotless (Palantir Java Format) ở phase `compile` của Maven — chạy
  `./mvnw compile` sẽ format lại **toàn bộ** file Java, không chỉ file vừa sửa. Muốn compile mà không
  format lại: thêm `-Dspotless.apply.skip=true`.
