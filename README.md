# elsa_store

Backend Spring Boot 3.5 / Java 21 của ứng dụng bán hàng thời trang Elsa Store (đồ án tốt nghiệp).
API nằm dưới `/elsa-store/api`, dùng MySQL, xác thực JWT, thanh toán VNPay, email (Spring Mail) và
thông báo đẩy (Firebase Cloud Messaging), chat thời gian thực giữa khách và cửa hàng (WebSocket) và trợ lý
AI gợi ý sản phẩm (Google Gemini).

```bash
./mvnw spring-boot:run      # cần MySQL local: localhost:3306/elsa_store_db (xem application.yaml)
./mvnw test                 # chạy test
docker compose up -d --build   # hoặc chạy cả MySQL + BE bằng Docker (xem "Chạy bằng Docker")
```

Bộ request mẫu cho toàn bộ API: [`postman/elsa_store.postman_collection.json`](postman/elsa_store.postman_collection.json)
(xem [Bộ request Postman](#bộ-request-postman)).

Tài khoản quản trị mặc định `admin123` / `admin123` được tạo khi khởi động nếu chưa có (xem
[Vai trò và phân quyền](#vai-trò-và-phân-quyền)).

## Thông báo, push và email

Cả email và push đều **tuỳ chọn**: chưa cấu hình thì BE vẫn chạy, thông báo trong app vẫn có, email
chỉ được ghi log.

| Sự kiện | Ai nhận | Kênh |
|---|---|---|
| Khách đặt đơn (`POST /orders`) | Khách | Trong app + push + email xác nhận |
| Khách đặt đơn | Chủ, quản lý, nhân viên của cửa hàng | Trong app + push |
| Đơn chuyển sang đã xác nhận / đang giao / đã huỷ (kèm lý do) / hoàn thành | Khách | Trong app + push + email |
| Khách tự huỷ đơn / bấm đã nhận hàng | Chủ, quản lý, nhân viên của cửa hàng | Trong app + push |
| Một đơn làm sản phẩm vừa xuống dưới mức cảnh báo tồn kho hoặc hết hàng (`LOW_STOCK`, mở `elsastore://inventory`) | Chủ, quản lý (nhân viên không xem được kho) | Trong app + push |
| Khách viết đánh giá mới (`REVIEW_NEW`, mở `elsastore://reviews`; sửa đánh giá thì không báo lại) | Chủ, quản lý, nhân viên | Trong app + push |
| Cửa hàng trả lời / sửa câu trả lời đánh giá (`REVIEW_REPLIED`, mở `elsastore://products/{id}`) | Khách viết đánh giá | Trong app + push |
| Tiền VNPay về cho đơn đã huỷ | Chủ, quản lý, nhân viên | Trong app + push ("Cần hoàn tiền cho khách") |
| Khách gửi yêu cầu trả hàng | Chủ, quản lý, nhân viên | Trong app + push (`RETURN_REQUESTED`) |
| Yêu cầu trả hàng được duyệt / bị từ chối / đã hoàn tiền | Khách | Trong app + push (`RETURN_UPDATED`) |
| Thanh toán chuyển sang thành công / thất bại (callback VNPay hoặc `PUT /orders/{id}/status?paymentStatus=`) | Khách | Trong app + push + email |
| Có hồ sơ mở cửa hàng mới (hoặc chủ gửi lại hồ sơ) | Admin | Trong app + push |
| Hồ sơ được duyệt / bị từ chối (kèm lý do) | Chủ cửa hàng | Trong app + push + email |

Chỉ báo khi trạng thái **thật sự đổi** (VNPay gọi lại nhiều lần không gửi trùng). Người nhận là tài
khoản của khách hàng gắn với đơn, nếu đơn không có khách hàng thì là người tạo đơn.

- `Notification` là hộp thông báo trong app: `GET /notifications`, `GET /notifications/unread-count`,
  `PUT /notifications/{id}/read`, `PUT /notifications/read-all`. Nội dung nằm trong
  `utils/NotificationMessages`, deep link trong `utils/AppLinks` (`elsastore://orders`,
  `elsastore://notifications`, `elsastore://store-status`, `elsastore://store-registrations/{id}`,
  `elsastore://chat/{id}`). Tin chat chỉ push, không tạo thông báo trong app — xem [Chat](#chat-giữa-khách-và-cửa-hàng).
- Push đi qua FCM HTTP v1 (`FcmPushNotificationSender`, xác thực bằng service account) tới các
  `DeviceToken` mà app đăng ký bằng `POST /notifications/devices` và gỡ bằng
  `DELETE /notifications/devices?token=` trước khi đăng xuất. Token FCM báo không còn đăng ký thì bị xoá.
  `POST /notifications/devices/test` gửi push thử (xem bên dưới).
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

**Khi app chạy nền hoặc đã tắt:** mọi push (đơn hàng, thanh toán, duyệt cửa hàng, chat) gửi kèm khối `notification` để
chính hệ điều hành hiện thông báo, `android.priority: high` (đánh thức máy đang ở chế độ Doze) và vào kênh Android
`elsa_store_alerts` mà app tạo sẵn ở mức ưu tiên cao — bật lên đầu màn hình, có âm thanh, thay vì nằm im trong kênh mặc
định (`FcmPushNotificationSender.message`; iOS có âm thanh mặc định). Tên kênh phải trùng ở ba nơi: BE
(`ANDROID_CHANNEL_ID`), `MainActivity.kt` và `AndroidManifest.xml` của app — `FcmPushNotificationSenderTest` kiểm tra
cả ba. Tin chat chỉ đẩy cho người **không** có WebSocket đang mở; socket im lặng quá 70 giây (điện thoại rớt mạng hay bị
hệ điều hành tắt app mà không kịp đóng) bị BE đóng (`WebSocketConfig`) để người đó không bị bỏ sót push.

**Thử nhận thông báo:** `POST /notifications/devices/test?delaySeconds=10` (cần đăng nhập, mọi vai trò kể cả ADMIN) gửi
"Thông báo thử" tới các thiết bị của **chính tài khoản đang gọi** sau `delaySeconds` giây (0–60, ngoài khoảng thì kẹp lại)
để kịp vuốt tắt app; không tạo dòng trong hộp thông báo, chạm vào mở `elsastore://notifications`. Trả
`{pushEnabled, devices, delaySeconds}`: `pushEnabled: false` = BE chưa có `FIREBASE_CREDENTIALS`, `devices: 0` = máy chưa
đăng ký token (đăng xuất rồi đăng nhập lại). App có nút này ở màn **Nhận thông báo khi tắt app** (xem README FE).

## Chat giữa khách và cửa hàng

Khách hàng (`USER`) nhắn tin với **cửa hàng**, không với từng nhân viên: mỗi cặp (khách, cửa hàng) có đúng một
`Conversation`, chủ / quản lý / nhân viên của cửa hàng dùng chung một hộp thư và số tin chưa đọc. `ADMIN` không
tham gia chat. Mọi kiểm tra quyền đi qua `AccessGuard`, tin nhắn chỉ đính kèm được sản phẩm **của chính cửa hàng đó**.

| Endpoint | Ai gọi | Việc |
|---|---|---|
| `POST /conversations` `{storeId}` | Khách | Mở (hoặc lấy lại) cuộc trò chuyện với cửa hàng |
| `GET /conversations/me` | Khách | Danh sách cuộc trò chuyện của mình (phân trang) |
| `GET /conversations/inbox` | Chủ, quản lý, nhân viên | Hộp thư của cửa hàng (phân trang) |
| `GET /conversations/unread-count` | Mọi người dùng chat | Số tin chưa đọc `{unread}` |
| `GET /conversations/{id}`, `GET /conversations/{id}/messages?beforeId=&size=` | Hai phía | Chi tiết, lịch sử (cuộn ngược theo `beforeId`) |
| `POST /conversations/{id}/messages` `{content, productIds?, askAi?}` | Hai phía | Gửi tin (≤ 2000 ký tự, ≤ 3 sản phẩm) |
| `PUT /conversations/{id}/read` | Hai phía | Đánh dấu đã đọc |

**Thời gian thực:** `ws://<host>/elsa-store/api/ws/chat?token=<accessToken>` (WebSocket thuần, không STOMP; trình duyệt
không đặt được header nên token đi theo query, header `Authorization: Bearer` cũng được). Token đi qua
`CustomJwtDecoder` nên token đã đăng xuất, tài khoản bị khoá và `ADMIN` đều bị từ chối lúc bắt tay. Mỗi sự kiện là
`{"type": "CONNECTED|MESSAGE|READ|PONG", "conversation": {…}, "message": {…}}`; client gửi `{"type":"PING"}` để giữ kết nối.
Tin được phát tới mọi socket đang mở của cả hai phía **sau khi giao dịch commit** (`ChatEventListener`), nên thiết bị
thứ hai hay nhân viên khác của cửa hàng cũng thấy ngay. Người nhận không có socket nào đang mở được **push FCM** với
`type=CHAT_MESSAGE`, `link=elsastore://chat/{id}` và `tag=chat-{id}` (Android gộp tin mới thay tin cũ của cùng cuộc
trò chuyện); tin chat không tạo dòng trong hộp thông báo.

Hai người gửi cùng lúc vào một cuộc trò chuyện cập nhật cùng một dòng `Conversation` (tin cuối, số chưa đọc), nên
`ChatServiceImpl.send` khoá dòng đó (`ConversationRepository.findForUpdate`, `PESSIMISTIC_WRITE`) — thiếu khoá này MySQL
báo deadlock 1213 khi nhắn dồn dập. `open` thử lại một lần nếu hai yêu cầu cùng tạo cuộc trò chuyện (ràng buộc duy nhất).
Danh sách socket nằm trong bộ nhớ (`ChatSessionRegistry`), nên chạy một instance; nhiều instance cần thêm Redis pub/sub.

## Trợ lý AI (Gemini)

Trợ lý tư vấn sản phẩm dựa trên **dữ liệu thật** của cửa hàng: chỉ gợi ý 1–3 sản phẩm đang bán, còn hàng và đúng cửa
hàng; mã sản phẩm do mô hình đưa ra bị BE kiểm tra lại (`recommend_products`) nên không thể bịa sản phẩm hay giá. Khoá API
chỉ nằm ở BE; chưa có khoá thì mọi tính năng AI tự tắt và app ẩn các nút AI (`GET /ai/status` → `{enabled, provider}`).

| Chỗ dùng | Cách kích hoạt | Kết quả |
|---|---|---|
| Trong chat, khách hỏi AI | `POST /conversations/{id}/messages` với `askAi: true` | Trợ lý trả lời ngay thay cửa hàng, kèm thẻ sản phẩm; tin AI có `aiGenerated=true` (nhãn "Trợ lý AI") |
| Trong chat, nhân viên cần gợi ý | `POST /ai/conversations/{id}/suggest-reply` | Trả **bản nháp** + sản phẩm để nhân viên sửa rồi gửi — không bao giờ tự gửi tới khách |
| Cửa hàng bật tự trả lời | `PUT /ai/store-settings` `{autoReply}` (chủ / quản lý), đọc bằng `GET /ai/store-settings` (`{storeId, autoReply, aiEnabled, autoReplyDelaySeconds}`) | Khách nhắn mà sau `AI_AUTO_REPLY_DELAY_SECONDS` chưa ai của cửa hàng trả lời thì trợ lý trả lời; mặc định **tắt** |
| Trợ lý mua sắm toàn sàn | `POST /ai/ask` `{question, history?}` (công khai) | Tư vấn trên các sản phẩm công khai của mọi cửa hàng, kèm thẻ sản phẩm |

- **Chuyển cho nhân viên:** trong chat, khi khách cần việc ngoài tư vấn (huỷ / đổi trả đơn, khiếu nại, khách muốn gặp
  người) trợ lý gọi `handoff_to_staff`; cuộc trò chuyện được gắn `needsStaff=true` (kèm `handoffReason`, chỉ lộ ở phía cửa
  hàng) để app hiện nhãn "Cần nhân viên" và banner, cờ này tự xoá khi một nhân viên trả lời. Trợ lý toàn sàn không có
  công cụ này vì không có nhân viên để chuyển.
- **Giới hạn lượt** (`AiRateLimiter`, cửa sổ trượt, trong bộ nhớ): khách hỏi AI trong chat 15 lượt / 10 phút / tài khoản và
  40 lượt / giờ / cuộc trò chuyện; tự trả lời 20 lượt / giờ / cuộc trò chuyện; gợi ý cho nhân viên 30 lượt / 10 phút /
  tài khoản; `/ai/ask` 20 lượt / 10 phút / tài khoản (hoặc theo IP khi chưa đăng nhập). Quá hạn mức: `1016`.
- **Cách hoạt động:** `AiClient` nhận yêu cầu dạng Anthropic và dịch sang Chat Completions chuẩn OpenAI (`OpenAiCompat`,
  giữ nguyên khối `thought signature` của Gemini), nên cùng một đường đi dùng được cho Gemini, Claude hay máy chủ
  OpenAI khác (Groq, OpenRouter, Ollama…). Vòng gọi công cụ tối đa 5 lượt; `search_products` xếp hạng bằng so khớp
  tiền tố từ **không phân biệt dấu** (`ProductKnowledge.rank`), nên "giay chay bo" vẫn ra "Giày chạy bộ". Lịch sử hội thoại
  được bọc trong thẻ `<conversation>` (tên thẻ trong tin nhắn bị gỡ) để chặn chèn lệnh vào prompt.
- **Dự phòng model:** model chính quá tải (5xx) thì thử lại rồi chuyển sang model kế tiếp
  (mặc định `gemini-3.8-flash` → `gemini-3.5-flash` → `gemini-2.5-flash`); hết lượt (429) báo `1018` thay vì treo.
- **Mã lỗi:** `1015` AI chưa cấu hình / không dùng được, `1016` quá nhiều lượt, `1017` model quá tải, `1018` hết hạn mức.
  Trong chat, lỗi AI **không làm mất tin của khách**: tin vẫn được lưu và trợ lý đáp một câu xin lỗi ngắn.

Bật bằng cách đặt khoá Google AI Studio (có gói miễn phí): `GEMINI_API_KEY=…` (xem [Biến môi trường](#biến-môi-trường)).
Thử mà không cần khoá thật: `AI_BASE_URL` trỏ tới một máy chủ giả nói giao thức OpenAI (test của repo dùng
`StubModelServer`, xem `src/test/java/.../ai`).

## Đơn hàng, giá và thanh toán

**Giá luôn do BE tính** (`PricingService`), app chỉ gửi biến thể và số lượng. `POST /orders/quote` `{items, couponCode?}`
trả báo giá y như lúc đặt (giá gốc, giá sau khuyến mãi từng dòng, giảm cả đơn, mã giảm giá, tồn kho từng dòng), không
trừ kho và không tạo đơn — app dùng để hiện màn thanh toán.

- Khuyến mãi `type` 1 = giảm tiền, 2 = giảm phần trăm (≤ 100); chỉ khuyến mãi `status = 1` và còn hạn được áp. Khuyến
  mãi **không mã** gắn sản phẩm → hạ đơn giá từng sản phẩm; khuyến mãi không mã, không gắn sản phẩm → giảm cả đơn khi đạt
  `minOrderValue`; **mã giảm giá** thay cho khoản giảm cả đơn tự động (không cộng dồn). Tiền làm tròn đồng.
- `GET /promotions/coupons?storeId=` (**công khai**): mã giảm giá đang chạy của cửa hàng kèm `productIds` (rỗng = mọi
  sản phẩm) — app hiện ở trang sản phẩm và bước thanh toán.
- Sản phẩm / phân loại đang được giảm có `salePrice`, `promotionName` và `promotionEndsAt` — lúc khuyến mãi hết hiệu
  lực: `endDate` tính trọn ngày nên là 00:00 ngày hôm sau (`null` khi không đặt ngày kết thúc). App dùng để đếm ngược.
- Tạo / sửa khuyến mãi (`POST`, `PUT /promotions`, chủ / quản lý) gửi luôn `productIds` để khuyến mãi và danh sách sản
  phẩm được lưu **trong cùng một giao dịch** — không thể có khuyến mãi "giảm giá giày" lưu xong mà gắn sản phẩm lỗi rồi
  thành giảm cả cửa hàng. `productIds` bỏ trống = giữ nguyên, `[]` = gỡ hết (thành giảm cả đơn nếu không có mã). API cũ
  `PUT /promotions/{id}/products` vẫn dùng được.
- **Tạm nghỉ bán:** `PUT /stores/me/pause` `{paused, note?}` (chủ / quản lý). Khi bật, báo giá và đặt đơn của khách bị
  từ chối kèm lời nhắn ("Cửa hàng đang tạm nghỉ, chưa nhận đơn mới: Nghỉ Tết đến 05/02"); khách vẫn xem sản phẩm, đơn tại
  quầy vẫn bán được. Trang cửa hàng công khai và chi tiết sản phẩm trả `paused` / `storePaused` và lời nhắn.
- **Danh sách đơn có phân trang:** `GET /orders/page?status&keyword&storeId&page&size` (mới đặt trước, tối đa 50 / trang,
  không kèm `timeline`); `keyword` tìm trong mã đơn, tên / SĐT người nhận và khách (không phân biệt hoa thường, dấu).
  `GET /orders/counts` trả số đơn từng trạng thái kèm `ALL`. Phạm vi như `GET /orders`: khách thấy đơn của mình, người
  của cửa hàng chỉ thấy cửa hàng mình (`storeId` bị bỏ qua), ADMIN xem mọi đơn hoặc một cửa hàng. `GET /orders` (trả
  hết một lần) vẫn giữ cho bản app cũ.
- **Xuất danh sách đơn ra Excel:** `GET /orders/export?status&keyword&from&to&storeId` (cùng bộ lọc, `from` / `to` là
  ngày đặt) trả tệp gồm sheet "Đơn hàng" (trạng thái, thanh toán, kênh bán, khách, người nhận, tiền hàng / giảm / phí
  giao / khách trả, người lập, lý do huỷ, dòng tổng không tính đơn huỷ) và sheet "Chi tiết" (từng món, SKU, đơn giá).
  Tối đa 5.000 đơn mới nhất. Chỉ chủ / quản lý (cửa hàng mình) và ADMIN — tệp có SĐT, địa chỉ khách nên nhân viên không
  tải được.
- **Phí giao hàng** theo cửa hàng: chủ / quản lý đặt bằng `PUT /stores/me/shipping` `{shippingFee, freeShippingThreshold}`
  (làm tròn đồng; phí trống / 0 = giao miễn phí và bỏ luôn mức miễn phí). Đơn khách đặt có tiền hàng sau giảm từ
  `freeShippingThreshold` được miễn phí giao; đơn tại quầy không tính phí. Báo giá trả `shippingFee`,
  `freeShippingThreshold` và `amountToFreeShipping` (còn thiếu bao nhiêu để được miễn phí) — `total` đã gồm phí giao.
  Mã giảm giá / mức đơn tối thiểu xét trên tiền hàng, không tính phí giao. Trang cửa hàng và chi tiết sản phẩm (công
  khai) cũng có `shippingFee`, `freeShippingThreshold`.
- Đơn lưu `totalAmount` (tiền hàng giá gốc), `discountAmount` (mọi khoản giảm), `shippingFee` và `finalAmount` (khách trả
  = gốc − giảm + phí giao, cũng là số tiền gửi sang VNPay); từng dòng lưu giá gốc, giá bán, phần giảm và **giá vốn lúc
  bán** (`order_items.cost_price`). Mã đơn `ORD-yyMMdd-XXXXXX`.
- **Tồn kho:** biến thể **chưa có dòng tồn kho** là "không theo dõi kho" — bán không giới hạn (dữ liệu cũ vẫn chạy như
  trước). Biến thể đã theo dõi thì trừ kho nguyên tử khi đặt (`UPDATE … WHERE quantity >= :q`, khoá theo thứ tự id biến
  thể nên hai đơn cùng lúc không deadlock và kho không bao giờ âm); hết hàng báo `1023` kèm số còn lại. Huỷ / xoá đơn
  chưa giao thì hoàn đúng phần đã trừ, mỗi dòng một lần.
- **Trạng thái:** `CHUA_XAC_NHAN → DA_XAC_NHAN → DANG_GIAO → HOAN_THANH`. Đơn không có địa chỉ giao (bán tại quầy) bỏ
  qua "đang giao", hoàn thành thẳng từ "đã xác nhận". Huỷ được khi chưa hoàn thành và chưa thanh toán — đang giao mà giao
  không thành thì cửa hàng huỷ để hàng về kho; bước sai báo `1024`. Khách huỷ được đơn của mình khi cửa hàng chưa xác
  nhận, và bấm **đã nhận được hàng** (`HOAN_THANH`) khi đơn đang giao (bấm lại không lỗi); hai việc này báo cho cửa hàng
  thay vì báo lại cho khách. Mỗi đơn trả `allowedNextStatuses` để app chỉ hiện nút hợp lệ. Đơn COD hoàn thành tự chuyển
  "đã thanh toán"; lúc hoàn thành được ghi vào `orders.completed_at` — ngày ghi nhận doanh thu.
- **Lý do huỷ và dòng thời gian:** `PUT /orders/{id}/status?status=&reason=` — `reason` (≤ 255 ký tự) là lý do huỷ, lưu
  ở `cancelReason` và đi kèm thông báo / email gửi khách. Mỗi lần đổi trạng thái (kể cả VNPay tự xác nhận khi đã trả)
  ghi một mốc vào `order_status_history`; API một đơn (`GET /orders/{id}`, đổi trạng thái) trả `timeline`: trạng thái,
  lúc nào, ai làm (`byRole` = `CUSTOMER` / `STORE` / `SYSTEM`; khách không thấy tên nhân viên), ghi chú. Đơn đặt trước
  khi có bảng này được dựng lại từ ngày đặt / ngày hoàn thành.
- **Tự xử lý đơn bị bỏ quên** (`OrderAutomation`, mỗi 5 phút): đơn VNPay chưa trả sau 30 phút (tính từ lúc đặt và lần
  mở link thanh toán gần nhất; link VNPay sống 15 phút) bị huỷ với lý do "Quá hạn thanh toán VNPay", hàng về kho; đơn
  đang giao quá 7 ngày mà khách chưa bấm đã nhận thì tự hoàn thành (COD coi như đã thu). Mốc trên dòng thời gian ghi
  `byRole = SYSTEM`. Nếu tiền VNPay vẫn về cho một đơn đã huỷ, đơn giữ trạng thái huỷ, ghi "đã thanh toán" và cửa hàng
  nhận thông báo "Cần hoàn tiền cho khách".
- **VNPay** (sandbox mặc định): `GET /payment/vn-pay?orderId=` tạo link với số tiền lấy từ đơn trong DB (đơn dưới 5.000đ
  hoặc đã huỷ / đã trả bị từ chối). `GET /payment/vn-pay-callback` (trình duyệt / app quay về) và `GET /payment/vnpay-ipn`
  (VNPay gọi máy chủ) đều công khai và **xác thực chữ ký HMAC-SHA512** cùng số tiền; xử lý lặp lại không đổi gì thêm
  (IPN trả mã `02` khi đã xác nhận). Trình duyệt mở callback nhận trang kết quả có nút mở app.

## Ảnh sản phẩm

Sản phẩm có **bộ ảnh** (tối đa 10) và một **ảnh đại diện** (`products.image_url`, hiện ở danh sách, giỏ, đơn, chat).
App tạo sản phẩm bằng JSON rồi tải ảnh sau; tạo bằng multipart kèm `files` vẫn được.

| API | Ý nghĩa |
|---|---|
| `POST /products/{id}/images` (multipart, phần `files`, nhiều ảnh) | Thêm ảnh; sản phẩm chưa có ảnh đại diện thì lấy ảnh đầu. Quá 10 ảnh → 400 |
| `GET /products/{id}/images` | Bộ ảnh kèm `id`, `url`, `cover` (người của cửa hàng, ADMIN) |
| `PUT /products/{id}/images/{imageId}/cover` | Đặt làm ảnh đại diện |
| `DELETE /products/{id}/images/{imageId}` | Gỡ ảnh; gỡ ảnh đại diện thì ảnh còn lại đầu tiên thay vào. File giữ lại trên đĩa vì tin nhắn chat cũ còn trỏ tới |
| `POST /product-variants/{id}/images` | Đổi ảnh của phân loại (ảnh vừa tải thành ảnh của phân loại) |

Chủ, quản lý, nhân viên của cửa hàng được thêm / gỡ / đổi ảnh; ảnh được kiểm tra theo nội dung tệp (xem Bảo mật).

**Trang chủ:** `GET /home/highlights?size=10` (công khai, tối đa 20 mỗi dải) — `onSale`: sản phẩm đang bán có khuyến mãi
tự động đang chạy và thật sự được giảm, giảm nhiều trước; `bestSellers`: sản phẩm bán nhiều món nhất trong đơn hoàn
thành 30 ngày qua (chưa có thì tính toàn bộ lịch sử), bỏ sản phẩm đang ẩn.

**Gợi ý tìm kiếm:** `GET /search/suggestions?keyword=&size=8` (công khai) — từ 2 ký tự: tên sản phẩm đang bán chứa từ
khoá (không trùng, mới trước, tối đa 10) và tối đa 3 cửa hàng đã duyệt khớp tên. Không phân biệt hoa thường / dấu (theo
collation MySQL); `%`, `_` trong từ khoá được thoát nên không thành ký tự đại diện.

**Sản phẩm tương tự:** `GET /products/{id}/related?size=8` (công khai, tối đa 20) — sản phẩm đang bán cùng danh mục, mới
nhất trước; chưa đủ thì bù hàng cùng cửa hàng; không gồm chính sản phẩm, không lặp. Sản phẩm đang ẩn trả 404.

**Ảnh cửa hàng (logo):** `POST /stores/me/image` (multipart, phần `file`) lưu vào `/uploads/stores/` và đặt làm ảnh
cửa hàng; `DELETE /stores/me/image` gỡ. Chủ và quản lý; ảnh hiện ở trang cửa hàng công khai và khung chat.

## Đánh giá sản phẩm và yêu thích

**Đánh giá** (`product_reviews`): chỉ tài khoản khách (`USER`) có **đơn hoàn thành chứa sản phẩm** mới viết được, mỗi
khách một đánh giá cho mỗi sản phẩm (viết lại = sửa, đánh dấu "đã sửa"). Đánh giá ghi kèm phân loại và mã đơn đã mua.
Tên người viết được che (`khach001` → `k******1`). Danh sách / tìm kiếm / chi tiết sản phẩm trả thêm `ratingAverage`,
`ratingCount` và `soldCount` (số món của đơn hoàn thành; đơn tại quầy chờ xác nhận chưa tính) — tính gộp một truy vấn
cho cả trang.

| Endpoint | Ai | Việc |
|---|---|---|
| `GET /products/{id}/reviews?rating&withImages&page&size` | công khai | Đánh giá mới nhất trước, lọc theo số sao / chỉ đánh giá có ảnh; mỗi đánh giá có `images` |
| `GET /products/{id}/reviews/summary` | công khai | `average`, `count`, `distribution` (số đánh giá từng mức 1–5 sao), `withImages` (số đánh giá có ảnh) |
| `GET /products/{id}/reviews/me` | công khai | `canReview`, `reason` (`NOT_SIGNED_IN` / `NOT_CUSTOMER` / `NOT_PURCHASED`) và đánh giá đã viết nếu có |
| `POST /products/{id}/reviews` `{rating 1–5, comment ≤ 1000 ký tự}` | khách đã nhận hàng | Viết / sửa đánh giá của mình; chưa mua hoặc không phải khách báo `1028` |
| `POST /products/{id}/reviews/me/images` (multipart, phần `files`) | người viết | Đính kèm ảnh vào đánh giá của mình (viết đánh giá trước), tối đa 5 ảnh, lưu ở `/uploads/reviews/` |
| `DELETE /products/{id}/reviews/me/images?url=` | người viết | Gỡ một ảnh khỏi đánh giá của mình |
| `DELETE /reviews/{id}` | người viết | Xoá đánh giá của mình (kèm danh sách ảnh) |
| `GET /reviews/store?rating&unreplied&page&size` | chủ, quản lý, nhân viên | Đánh giá mọi sản phẩm của cửa hàng, lọc số sao / chưa trả lời |
| `PUT /reviews/{id}/reply` `{reply}` | chủ, quản lý, nhân viên | Trả lời công khai dưới đánh giá (gửi lại = sửa) |

**Trang cửa hàng công khai:** `GET /stores/{id}` (không cần đăng nhập, chỉ cửa hàng đã duyệt) trả tên, ảnh, địa chỉ, SĐT,
giới thiệu, chính sách giao hàng và thêm `ratingAverage` / `ratingCount` (gộp đánh giá mọi sản phẩm), `productCount`
(sản phẩm đang bán), `soldCount` (số món của đơn đã hoàn thành); không có email, tài khoản của chủ. Sản phẩm của cửa hàng
lấy bằng `POST /products/search` với `storeId`.

**Yêu thích** (`wishlist_items`, chỉ tài khoản khách): `GET /wishlist` (sản phẩm đã thả tim, mới nhất trước, đủ giá
khuyến mãi / còn hàng / điểm đánh giá như danh sách sản phẩm), `GET /wishlist/ids` (mảng id để app tô tim nhanh),
`POST /wishlist/{productId}` (bấm lại không lỗi, tối đa 200 sản phẩm) và `DELETE /wishlist/{productId}` — hai lệnh này
trả lại mảng id mới. Sản phẩm bị ẩn tự biến khỏi danh sách; xoá sản phẩm / tài khoản thì dòng yêu thích được dọn theo.

## Đổi / trả hàng

Khách gửi yêu cầu trả hàng cho đơn **đã hoàn thành** trong 7 ngày (`ORDER_RETURN_WINDOW_DAYS`) kể từ lúc hoàn thành; đơn
trả về `returnDeadline`. Mỗi đơn chỉ một yêu cầu đang mở, tổng số trả không vượt số đã mua (trả nhiều lần được).
`CHO_DUYET → DA_DUYET → DA_HOAN_TIEN`, hoặc `TU_CHOI` (cửa hàng, bắt buộc lý do) / `DA_HUY` (khách huỷ khi còn chờ).
Mã yêu cầu `TH-yyMMdd-XXXX`.

| Endpoint | Ai | Việc |
|---|---|---|
| `POST /returns` `{orderId, items: [{productVariantId, quantity}], reason, note?}` | khách mua đơn | Gửi yêu cầu; BE tính `suggestedRefund` = giá khách đã trả cho các món (giảm giá cả đơn chia đều theo giá trị, **không hoàn phí giao**) |
| `GET /returns/me?orderId&page&size` | khách | Yêu cầu của mình (lọc theo đơn) |
| `PUT /returns/{id}/cancel` | khách | Huỷ khi còn chờ duyệt |
| `GET /returns?status&storeId&page&size` | chủ, quản lý, nhân viên; ADMIN xem | Yêu cầu gửi tới cửa hàng |
| `GET /returns/{id}` | khách của yêu cầu, người của cửa hàng, ADMIN | Chi tiết + `allowedActions` của người xem |
| `PUT /returns/{id}/approve` `{note?}` | chủ, quản lý, nhân viên | Đồng ý, nhắn khách cách gửi hàng |
| `PUT /returns/{id}/reject` `{note}` | chủ, quản lý, nhân viên | Từ chối kèm lý do |
| `PUT /returns/{id}/refund` `{refundAmount?, note?}` | chủ, quản lý | Đã nhận hàng và hoàn tiền (mặc định số gợi ý, không quá số khách đã trả); hàng về kho (thẻ kho `RETURN`, ghi "Khách trả hàng TH-…"); hoàn thẳng từ chờ duyệt được (khách mang hàng tới quầy) |

Mỗi bước báo cho bên kia (`RETURN_REQUESTED` cho cửa hàng, `RETURN_UPDATED` cho khách, mở `elsastore://returns`). Báo
cáo tổng hợp có `refundTotal`, `refundCount` (theo ngày hoàn tiền) và `netAfterRefunds` = doanh thu thực thu − tiền hoàn;
tổng quan có `pendingReturns` và `unrepliedReviews` (đánh giá chưa trả lời). Lợi nhuận gộp chưa trừ hàng trả.

## Doanh thu, kho và nhập hàng

Chỉ chủ và quản lý của cửa hàng dùng các API dưới đây; ADMIN xem báo cáo (chọn `storeId` hoặc toàn hệ thống) và phiếu
nhập, không sửa gì. Nhân viên không xem được.

**Doanh thu** chỉ tính đơn `HOAN_THANH` (tham số `statuses` đổi được), **ghi nhận vào ngày hoàn thành**
(`coalesce(completed_at, order_date)`: đơn cũ chưa có cột này dùng ngày đặt) — đơn đặt 30/9 giao ngày 2/10 là doanh thu
tháng 10. Tỷ lệ huỷ vẫn tính theo ngày đặt. Máy chủ luôn chạy giờ `Asia/Ho_Chi_Minh` (`ElsaStoreApplication`), nên "hôm
nay" đúng giờ Việt Nam dù máy chủ đặt UTC.

| Endpoint | Trả về |
|---|---|
| `GET /reports/dashboard` | Doanh thu hôm nay / hôm qua, tháng này / **cùng kỳ tháng trước** (cùng số ngày đầu tháng), lợi nhuận gộp tháng, đơn chờ xác nhận / đã xác nhận / hoàn thành, đơn đặt hôm nay, số biến thể sắp hết / hết hàng (khi xem một cửa hàng), doanh thu 7 ngày (đủ 7 điểm) |
| `GET /reports/summary?from&to` | Số đơn, tiền hàng gốc, giảm giá, phí giao hàng khách trả (`shippingTotal`), doanh thu thực thu, giá trị TB/đơn, **giá vốn, lợi nhuận gộp, biên lợi nhuận**, số lượng bán chưa có giá vốn, **kỳ liền trước cùng độ dài** (`previous`) và % tăng trưởng doanh thu / số đơn / lợi nhuận (`null` khi kỳ trước bằng 0) |
| `GET /reports/breakdown?from&to` | Doanh thu theo cách trả tiền (`COD`, `VNPAY`, `OTHER`) và kênh bán (`ONLINE` khách tự đặt, `COUNTER` lập tại quầy), kèm tỷ trọng |
| `GET /reports/revenue?groupBy=DAY\|WEEK\|MONTH\|YEAR`, `top-products` (kèm lợi nhuận, `null` khi còn món chưa có giá vốn), `revenue-by-category`, `top-customers`, `staff-performance`, `cancellation-rate`, `low-stock` | Như tên |
| `GET /reports/export?from&to` | Excel: tổng quan (có lợi nhuận và so với kỳ trước), doanh thu theo ngày, danh mục, sản phẩm (giá vốn, lãi), **kênh bán**, khách hàng, nhân viên, tồn kho thấp, **giá trị kho** (khi xem một cửa hàng) |

**Giá vốn và lợi nhuận:** mỗi biến thể có giá vốn bình quân (`product_variants.cost_price`, không lộ ra API công khai).
Nhập hàng tự tính lại theo **bình quân gia quyền** `(tồn × giá vốn cũ + số nhập × giá nhập) / (tồn + số nhập)`, làm tròn
đồng; chưa có giá vốn hoặc đang hết hàng thì lấy giá nhập mới. Đơn chụp giá vốn lúc bán nên đổi giá vốn về sau không làm
sai lợi nhuận kỳ cũ. Lợi nhuận gộp = doanh thu thực thu − giá vốn các món đã có giá vốn; còn món chưa có giá vốn thì báo
cáo trả `uncostedQuantity > 0` để app cảnh báo (lợi nhuận đang cao hơn thực tế).

**Kho hàng** (`/inventories`):

| Endpoint | Việc |
|---|---|
| `GET /inventories/stock?keyword&filter=ALL\|TRACKED\|UNTRACKED\|LOW\|OUT&page&size` | Mọi biến thể của cửa hàng (kể cả sản phẩm đang ẩn) với tồn, mức cảnh báo, giá bán, giá vốn, giá trị tồn, tình trạng |
| `GET /inventories/overview` | Số biến thể, chưa theo dõi / hết / sắp hết, tổng số lượng, **giá trị tồn theo giá vốn và theo giá bán**, số biến thể còn hàng chưa có giá vốn |
| `PUT /inventories/variants/{variantId}` `{quantity?, minQuantity?, costPrice?, note?}` | Kiểm kê (số đếm thực tế; biến thể chưa theo dõi thì bắt đầu theo dõi), mức cảnh báo, giá vốn — trường để trống giữ nguyên |
| `DELETE /inventories/variants/{variantId}` | Ngừng theo dõi kho (bán không giới hạn trở lại) |
| `GET /inventories/variants/{variantId}/movements` | **Thẻ kho**: mọi lần nhập (`IMPORT`), bán (`SALE`), hoàn (`RETURN`), kiểm kê (`ADJUST`) kèm số thay đổi, tồn sau thay đổi, giá, chứng từ (mã đơn / mã phiếu nhập), người làm |

**Cảnh báo tồn kho** (`StockAlerts`): sau mỗi đơn trừ kho, biến thể nào vừa **vượt xuống** mức cảnh báo (`minQuantity`)
hoặc về 0 thì chủ và quản lý nhận một thông báo gộp cho cả đơn; đã ở dưới mức từ trước thì không báo lại.

Mọi thay đổi số lượng — kể cả API cũ `POST/PUT/DELETE /inventories/{id}` — đi qua `StockService` để thẻ kho
(`stock_movements`) luôn khớp với con số tồn hiện tại.

**Phiếu nhập** (`/purchase-orders`): tạo phiếu **nháp** `{supplierId?, note?, items: [{productVariantId, quantity,
unitPrice}]}` (nhà cung cấp và sản phẩm phải của chính cửa hàng, mỗi biến thể một dòng), sửa / huỷ / xoá thoải mái;
`POST /purchase-orders/{id}/receive` cộng hàng vào kho, tính lại giá vốn và ghi thẻ kho cho từng dòng rồi **khoá phiếu**
(sửa / huỷ / xoá phiếu đã nhập báo `1025`). Phiếu bị khoá dòng khi nhập kho nên bấm nhiều lần cùng lúc cũng chỉ cộng một
lần. `POST /{id}/cancel` huỷ phiếu nháp; `GET /purchase-orders?status=0|1|2` liệt kê mới nhất trước. Mã phiếu
`PN-yyMMdd-XXXX`. API cũ `/purchase-order-items` (sửa thẳng dòng phiếu, kể cả phiếu đã nhập) đã bỏ.

## Bảo mật

- **JWT** HS512 (khoá `JWT_SIGNER_KEY` ≥ 64 byte, ngắn hơn thì BE không khởi động; còn dùng khoá mặc định thì log cảnh
  báo). Token mang số phiên bản `ver`: đổi mật khẩu (`PUT /users/me/password` `{currentPassword, newPassword}`, sai mật
  khẩu cũ báo `1022`), bị khoá tài khoản… làm mọi token cũ hết hiệu lực ngay. Refresh token không dùng thay access token
  được; token đã đăng xuất được dọn mỗi giờ.
- **Chặn dò mật khẩu** (`LoginAttemptLimiter`): sai 5 lần / tài khoản hoặc 20 lần / IP trong 15 phút thì tạm khoá đăng
  nhập (`1021`).
- **Quên mật khẩu** bằng mã 6 số gửi qua email (công khai, `identifier` = tên đăng nhập hoặc email):
  `POST /auth/password/forgot` `{identifier}` → `POST /auth/password/verify` `{identifier, code}` (kiểm, không tiêu mã) →
  `POST /auth/password/reset` `{identifier, code, newPassword}` (đổi mật khẩu, đăng xuất mọi thiết bị). Bước gửi mã luôn trả
  cùng một kết quả `{resendAfterSeconds, validMinutes}` để không lộ tài khoản / email nào tồn tại. Chỉ lưu **bản băm** của
  mã; mã sống 10 phút, dùng một lần, sai 5 lần thì hỏng (số lần sai vẫn được lưu dù API báo lỗi); tối đa 1 mã / phút và 5
  mã / giờ / tài khoản, 20 yêu cầu / giờ / IP (`1027`). Mã sai / hết hạn báo `1026`. **Chưa cấu hình mail** (máy phát
  triển) thì mã được ghi ra log BE ở mức WARN để còn thử được luồng này.
- Ảnh tải lên được kiểm tra theo **nội dung tệp** (JPEG, PNG, WEBP, GIF), không tin phần mở rộng. Lỗi không lường trước
  trả `999` với câu chung, chi tiết chỉ ghi log; dữ liệu đang được dùng / trùng báo `1019`, sửa đồng thời báo `1020`.

## Vai trò và phân quyền

Năm vai trò: `ADMIN`, `STORE_OWNER`, `STORE_MANAGER`, `STAFF`, `USER`. Mỗi cửa hàng (`Store`) là một
"tenant": sản phẩm, nhà cung cấp, khuyến mãi, phiếu nhập, đơn hàng và khách vãng lai đều thuộc về một
cửa hàng; danh mục là dùng chung và chỉ ADMIN quản lý. Mọi kiểm tra quyền đi qua `AccessGuard`
(`security/`), không rải trong từng service.

| Chức năng | ADMIN | STORE_OWNER | STORE_MANAGER | STAFF | USER / khách |
|---|:-:|:-:|:-:|:-:|:-:|
| Xem cửa hàng, sản phẩm, danh mục (công khai, không cần đăng nhập) | ✔ | ✔ | ✔ | ✔ | ✔ |
| Đăng ký mở cửa hàng (`POST /store-registrations`) | – | – | – | – | ✔ (công khai) |
| Duyệt / từ chối cửa hàng | ✔ | – | – | – | – |
| Quản lý danh mục, khoá / mở khoá tài khoản | ✔ | – | – | – | – |
| Tạo tài khoản STORE_MANAGER | – | ✔ | – | – | – |
| Tạo tài khoản STAFF | – | ✔ | ✔ | – | – |
| Thêm / sửa sản phẩm, biến thể | – | ✔ | ✔ | ✔ | – |
| Kho hàng (kiểm kê, giá vốn, thẻ kho), phiếu nhập | – (phiếu nhập: chỉ xem) | ✔ | ✔ | ✘ | – |
| **Xoá** sản phẩm, biến thể, khách hàng, đơn hàng | – | ✔ | ✔ | ✘ | – |
| Nhà cung cấp, khuyến mãi, phí giao hàng | – | ✔ | ✔ | ✘ | – |
| Khách hàng của cửa hàng, tạo đơn tại quầy, đổi trạng thái đơn | – | ✔ | ✔ | ✔ | – |
| Báo cáo doanh thu, lợi nhuận, kênh bán, sản phẩm, khách hàng, nhân viên, xuất Excel | ✔ (chỉ xem) | ✔ | ✔ | ✘ | – |
| Trả lời đánh giá sản phẩm của cửa hàng | – | ✔ | ✔ | ✔ | – |
| Duyệt / từ chối yêu cầu trả hàng | – (chỉ xem) | ✔ | ✔ | ✔ | – |
| Hoàn tiền trả hàng (hàng về kho) | – | ✔ | ✔ | ✘ | – |
| Giỏ hàng, đặt hàng, huỷ đơn chưa xác nhận của mình | – | – | – | – | ✔ |
| Đánh giá sản phẩm đã nhận, danh sách yêu thích, yêu cầu trả hàng | – | – | – | – | ✔ |

Quy tắc chính:

- **Xét duyệt cửa hàng**: `POST /store-registrations` (công khai) tạo tài khoản `STORE_OWNER` cùng cửa hàng
  ở trạng thái `PENDING`. Chủ đăng nhập được ngay nhưng chưa thêm được sản phẩm, tạo nhân sự hay bán hàng
  cho tới khi ADMIN gọi `PUT /store-registrations/{id}/approve`. Bị `reject` (kèm lý do) thì chủ sửa hồ sơ
  và gửi lại bằng `PUT /store-registrations/me`. ADMIN nhận thông báo khi có hồ sơ mới; chủ nhận thông báo
  và email khi có kết quả.
- **Nhân sự**: `/store-members` — chủ tạo quản lý và nhân viên, quản lý chỉ tạo nhân viên. Xoá tài khoản đã
  từng xử lý đơn thì BE khoá thay vì xoá. Tài khoản bị khoá không đăng nhập được và token đang dùng vô hiệu.
- **ADMIN chỉ xem** dữ liệu của cửa hàng (đơn hàng, báo cáo — có thể truyền `storeId`); mọi thao tác ghi bị
  chặn bằng `ADMIN_READ_ONLY`.
- **Một đơn hàng và một giỏ hàng chỉ chứa sản phẩm của một cửa hàng.** Đơn tại quầy do nhân viên lập ghi lại
  người lập (`handledBy`), khách có thể để trống (khách lẻ).
- **Báo cáo** (`/reports/*`): xem [Doanh thu, kho và nhập hàng](#doanh-thu-kho-và-nhập-hàng). `/revenues/*` cũ vẫn
  dùng được và trỏ cùng service.

### Nâng cấp từ dữ liệu cũ

Khi khởi động, `LegacySchemaMigration` chạy **trước Hibernate** để bỏ ràng buộc `check (role between 0 and 3)` mà
Hibernate cũ tạo cho cột số, rồi đổi cột `users.role` kiểu số cũ
(0 → ADMIN, 1 và 2 → STAFF, còn lại → USER) sang chuỗi, cho phép `customers.user_id` để trống và đổi `orders.status`,
`notifications.type` từ kiểu `enum(...)` của MySQL (Hibernate tạo, không bao giờ tự thêm giá trị) sang chuỗi để lưu được
trạng thái mới như `DANG_GIAO`, và gỡ các ràng buộc `check (… in ('A','B'))` mà Hibernate tạo cho cột enum lưu dạng chuỗi
trên CSDL mới (nếu không, giá trị thêm ở bản sau bị MySQL từ chối); sau đó
`LegacyDataMigration` gom sản phẩm, nhà cung cấp, khuyến mãi, phiếu nhập, đơn hàng và nhân sự chưa thuộc cửa
hàng nào vào cửa hàng mặc định **"Elsa Store"** (tạo chủ `owner123` / `owner123` nếu chưa có — hãy đổi mật
khẩu ngay). Hai bước đều chạy lại an toàn nhiều lần.

## Chạy bằng Docker

Không cần cài Java / MySQL — chỉ cần Docker Desktop:

```bash
cp .env.example .env        # sửa giá trị nếu cần; để trống = mặc định (sandbox VNPay, khoá JWT phát triển)
docker compose up -d --build
```

- `elsa-db`: MySQL 8.4, dữ liệu nằm trong volume `elsa-db-data`, mở ra máy ở cổng **3307** (không đụng MySQL đang chạy ở
  3306). `elsa-app`: BE ở `http://localhost:8080/elsa-store/api`, chờ MySQL sẵn sàng mới khởi động.
- Ảnh tải lên lưu ở `./uploads` trên máy; file service account Firebase đặt vào `./secrets/` rồi ghi
  `FIREBASE_CREDENTIALS=/app/secrets/<tên file>.json` trong `.env`.
- Điện thoại thật gọi BE qua IP LAN của máy (`http://192.168.x.x:8080/elsa-store/api`); đổi `APP_PUBLIC_URL` theo địa chỉ
  đó để link trong email và trang VNPay trả về đúng.
- Xem log: `docker compose logs -f elsa-app`. Dừng: `docker compose down` (thêm `-v` để **xoá luôn dữ liệu** MySQL).
- Image build bỏ qua Spotless và test (`Dockerfile`, hai giai đoạn: Maven + JDK 21 build, JRE 21 Alpine chạy, giờ
  `Asia/Ho_Chi_Minh`).

## Bộ request Postman

`postman/elsa_store.postman_collection.json` — 74 request chạy tuần tự đi hết một vòng nghiệp vụ: ADMIN duyệt cửa hàng →
chủ tạo nhân viên, sản phẩm, biến thể, kiểm kê, phiếu nhập, khuyến mãi, mã giảm giá, tạm nghỉ / mở lại, phí giao hàng → khách báo giá,
đặt hàng COD → nhân viên xác nhận, giao cho đơn vị vận chuyển → khách bấm đã nhận hàng, xem dòng thời gian, huỷ một đơn
kèm lý do, cửa hàng tìm đơn / đếm theo trạng thái → khách trả hàng, cửa hàng duyệt và hoàn tiền → khách đánh giá, cửa hàng trả lời, thả tim → đơn VNPay → báo cáo → các trường hợp bị chặn quyền → quên / đổi
mật khẩu. File được sinh từ `postman/build_collection.py` (sửa danh sách request ở đó rồi sinh lại; `--run <baseUrl>`
chạy thử cả bộ trên một BE đang chạy).

1. Postman → **Import** file trên; sửa biến `baseUrl` nếu BE không chạy ở `http://localhost:8080/elsa-store/api`.
2. Chuột phải collection → **Run collection** (hoặc chạy từng request từ trên xuống). Mỗi request tự lưu token / id vào
   Collection Variables cho bước sau và tự kiểm mã HTTP; mỗi lần chạy sinh `runSuffix` mới nên chạy lại không trùng tên.
3. Hai request "chạy tay" ở thư mục 6 cần mã OTP: lấy trong email (hoặc log BE khi chưa cấu hình mail), điền vào biến
   `resetCode`.

## Biến môi trường

Đặt trong cấu hình chạy của IDE hoặc biến môi trường hệ điều hành (Spring Boot không tự đọc file `.env`; file `.env` chỉ
dùng cho `docker compose`).

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `JWT_SIGNER_KEY` | khoá phát triển trong `application.yaml` | Khoá ký JWT HS512, tối thiểu 64 ký tự — **bắt buộc đổi khi triển khai** |
| `VNPAY_TMN_CODE` / `VNPAY_SECRET_KEY` / `VNPAY_URL` | tài khoản sandbox | Thông tin cổng VNPay |
| `VNPAY_RETURN_URL` | `{APP_PUBLIC_URL}/payment/vn-pay-callback` | Nơi VNPay chuyển người mua về |
| `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` | Máy chủ SMTP |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | trống | Tài khoản SMTP; trống thì chỉ ghi log email |
| `MAIL_FROM` | trống (dùng `MAIL_USERNAME`) | Địa chỉ người gửi |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `true` / `true` | Tắt khi thử với SMTP giả trên máy |
| `APP_PUBLIC_URL` | `http://localhost:8080/elsa-store/api` | Địa chỉ API công khai cho link trong email |
| `ORDER_UNPAID_CANCEL_MINUTES` | `30` | Tự huỷ đơn VNPay chưa trả sau chừng này phút (tối thiểu 20); `0` = tắt |
| `ORDER_AUTO_COMPLETE_DAYS` | `7` | Tự hoàn thành đơn đang giao sau chừng này ngày; `0` = tắt |
| `ORDER_RETURN_WINDOW_DAYS` | `7` | Khách gửi yêu cầu trả hàng trong chừng này ngày sau khi đơn hoàn thành; `0` = tắt |
| `FIREBASE_CREDENTIALS` | trống | Đường dẫn file JSON service account Firebase; trống thì tắt push |
| `GEMINI_API_KEY` | trống | Khoá Google AI Studio; có khoá thì bật trợ lý AI (provider tự chọn là `gemini`) |
| `ANTHROPIC_API_KEY` | trống | Dùng Claude thay Gemini (provider `anthropic`) |
| `AI_API_KEY` | trống | Khoá dùng chung cho mọi nhà cung cấp, kể cả máy chủ chuẩn OpenAI khác |
| `AI_PROVIDER` | tự chọn theo khoá | `gemini` · `anthropic` · `openai` |
| `AI_BASE_URL` | mặc định của nhà cung cấp | Địa chỉ máy chủ model (đặt khi dùng Groq / OpenRouter / Ollama hoặc máy chủ giả để thử) |
| `AI_ASSISTANT_MODEL` | `gemini-3.8-flash` (Gemini), `claude-sonnet-5` (Anthropic) | Model trợ lý tư vấn (phải hỗ trợ gọi công cụ) |
| `AI_ASSISTANT_FALLBACK_MODELS` | `gemini-3.5-flash,gemini-2.5-flash` | Model thử tiếp khi model chính quá tải / hết lượt; `none` để tắt |
| `AI_FAST_MODEL` / `AI_FAST_FALLBACK_MODELS` | theo nhà cung cấp | Được đọc cho đồng bộ với booking_app nhưng chưa có tính năng nào của elsa dùng |
| `AI_TIMEOUT_SECONDS` | `60` | Thời gian chờ một lời gọi model |
| `AI_AUTO_REPLY_DELAY_SECONDS` | `60` | Chờ bao lâu trước khi trợ lý tự trả lời thay cửa hàng đã bật tự trả lời |

Bảng `notifications`, `device_tokens`, `conversations`, `chat_messages`, `stock_movements`, `password_reset_codes`,
`product_reviews`, `wishlist_items`, `order_status_history`, `return_requests` và `return_items` được Hibernate tạo tự động
(`ddl-auto=update`); các cột mới trên bảng cũ (`stores.ai_auto_reply`, `stores.shipping_fee`,
`stores.free_shipping_threshold`, `orders.completed_at`, `orders.cancel_reason`, `orders.shipping_fee`,
`product_variants.cost_price`, `order_items.cost_price`, `purchase_orders.code/note/received_at`, `users.token_version`…)
được thêm cùng cách, đều cho phép để trống nên dữ liệu cũ không cần chuyển đổi.

## Ghi chú

- Test đơn vị: `./mvnw test -Dtest='!ElsaStoreApplicationTests'` (không cần MySQL); `ElsaStoreApplicationTests` dựng cả
  ứng dụng nên cần CSDL.
- CI (`.github/workflows/ci.yml`, GitHub Actions): mỗi lần push lên `main` / `develop` hoặc mở pull request thì chạy
  các test đơn vị trên, sau đó build thử Docker image và kiểm tra `docker-compose.yml`.
- Format code tự động chạy qua Spotless (Palantir Java Format) ở phase `compile` của Maven — chạy
  `./mvnw compile` sẽ format lại **toàn bộ** file Java, không chỉ file vừa sửa. Muốn compile mà không
  format lại: thêm `-Dspotless.apply.skip=true`.
