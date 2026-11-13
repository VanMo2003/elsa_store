# Elsa Store — tổng hợp chức năng

Tài liệu tóm tắt hệ thống theo **tác nhân** (dùng cho chương phân tích chức năng của báo cáo). Chi tiết kỹ thuật, quy
tắc nghiệp vụ và cách chạy nằm trong `README.md` của BE và FE; bộ request Postman ở `postman/`. Phụ lục cuối liệt kê
toàn bộ API (trích tự động từ controller).

## Tác nhân

| Tác nhân | Vào app | Tóm tắt quyền |
|---|---|---|
| Khách vãng lai | Không đăng nhập | Xem sản phẩm, cửa hàng, đánh giá, hỏi đáp, banner, tìm kiếm; giỏ / đặt hàng cần đăng nhập |
| Khách hàng (`USER`) | Tab Trang chủ / Đơn hàng / Tìm kiếm / Giỏ hàng / Tài khoản | Mua hàng, thanh toán COD / VNPay, xu & hạng thành viên, đánh giá, hỏi đáp, chat, trả hàng, theo dõi cửa hàng |
| Nhân viên (`STAFF`) | Khung nhân viên | Xử lý đơn, bán tại quầy, sản phẩm (không xoá), khách hàng, trả lời đánh giá / câu hỏi / chat |
| Quản lý (`STORE_MANAGER`) | Khung cửa hàng | Như chủ, chỉ tạo được nhân viên |
| Chủ cửa hàng (`STORE_OWNER`) | Hồ sơ chờ duyệt → khung cửa hàng | Toàn quyền cửa hàng: kho, nhập hàng, giá vốn, khuyến mãi, báo cáo, nhân sự, phí giao, tạm nghỉ |
| Quản trị (`ADMIN`) | Khung quản trị | Duyệt / đình chỉ cửa hàng, tài khoản, danh mục, banner, báo cáo vi phạm, xem đơn & báo cáo toàn sàn (chỉ xem dữ liệu cửa hàng) |

## Chức năng theo tác nhân

### Khách vãng lai và khách hàng
- **Tài khoản:** đăng ký, đăng nhập (khoá tạm khi sai nhiều lần), quên mật khẩu bằng mã OTP email, đổi mật khẩu (đăng
  xuất thiết bị khác), ảnh đại diện, sổ địa chỉ, **xoá tài khoản** (ẩn danh hoá, giữ đơn cho sổ sách).
- **Khám phá:** trang chủ có banner, đang giảm giá, bán chạy, đã xem gần đây, **gợi ý cho bạn**, hàng mới theo danh mục;
  tìm kiếm có gợi ý khi gõ, lịch sử, lọc danh mục / giá, sắp xếp; trang cửa hàng công khai (đánh giá, số đã bán, người
  theo dõi, sản phẩm); trang sản phẩm (bộ ảnh, phân loại, đếm ngược khuyến mãi, mã giảm giá của cửa hàng, **hướng dẫn chọn
  size**, đánh giá có ảnh + "hữu ích", **"Khách nói gì"** (AI tóm tắt đánh giá: ưu điểm, điểm cần lưu ý), hỏi đáp, "khách mua cũng mua", sản phẩm tương tự, chia sẻ liên kết).
- **Mua hàng:** giỏ hàng (giá luôn theo khuyến mãi hiện tại, báo hết hàng), báo giá có mã giảm giá + phí giao + **dùng
  xu**, đặt hàng COD / VNPay (tự huỷ đơn VNPay chưa trả sau 30 phút), theo dõi trạng thái + dòng thời gian, **mã vận đơn**
  (đơn vị vận chuyển + mã, sao chép để tra cứu), huỷ kèm lý do, bấm "đã nhận hàng" (nhắc trước 2 ngày, tự hoàn thành sau 7 ngày), mua lại, yêu cầu trả hàng trong 7 ngày.
- **Gắn kết:** yêu thích (báo giảm giá / có hàng lại), theo dõi cửa hàng (báo sản phẩm mới), xu thưởng 1% (Bạc / Vàng /
  Kim cương nhận x1,5 / x2 / x3), **điểm danh hằng ngày** (chuỗi 7 ngày 50 → 500 xu), **mời bạn bè** (mã giới thiệu: bạn mua đơn đầu từ 100.000đ thì người mời +5.000 xu, người mới +2.000 xu), thưởng xu khi đánh giá / có ảnh, nhắc giỏ hàng bị bỏ quên, tuỳ chọn tắt từng nhóm thông
  báo, chat với cửa hàng (gửi ảnh, đính kèm sản phẩm) và trợ lý AI, báo cáo sản phẩm vi phạm.

### Nhân viên, quản lý, chủ cửa hàng
- **Đơn hàng:** danh sách phân trang + đếm theo trạng thái, xác nhận → giao (kèm đơn vị vận chuyển + mã vận đơn, sửa được
  khi đang giao) → hoàn thành / huỷ, bán tại quầy, xuất Excel
  (chủ / quản lý), xử lý trả hàng (nhân viên duyệt, chủ / quản lý hoàn tiền).
- **Sản phẩm & kho:** sản phẩm, phân loại, bộ ảnh tối đa 10, ẩn / mở bán, **AI viết mô tả** (bản nháp từ tên, danh mục,
  màu / cỡ và gợi ý đã gõ; không có AI thì mẫu soạn sẵn), **nhập hàng loạt từ Excel** (tệp mẫu, xem trước,
  lỗi theo dòng, chủ / quản lý); kho với thẻ kho, kiểm kê, cảnh báo sắp hết;
  phiếu nhập (giá vốn bình quân gia quyền); nhà cung cấp.
- **Bán hàng:** khuyến mãi tự động và mã giảm giá, phí giao / mức miễn phí, tạm nghỉ bán kèm lời nhắn.
- **Chăm sóc khách:** hộp thư chat chung của cửa hàng (gợi ý trả lời bằng AI, **câu trả lời nhanh**, AI tự trả lời khi
  vắng), trả lời đánh giá và trả lời câu hỏi (**AI soạn bản nháp**: theo số sao / theo hồ sơ thật của sản phẩm), danh sách
  khách hàng.
- **Báo cáo (chủ / quản lý):** tổng quan (hôm nay / tháng, lợi nhuận gộp, **mục tiêu tháng + dự báo cuối tháng**, việc cần
  làm), **AI nhận xét kỳ** (tóm tắt, điểm nổi bật, việc nên làm từ số liệu thật; không có AI thì theo quy tắc), **giờ vàng**
  (bản đồ nhiệt đơn theo thứ × giờ đặt), doanh thu theo ngày / tuần / tháng /
  năm, so với kỳ trước, sản phẩm bán chạy kèm lãi, danh mục, kênh bán & cách trả tiền, khách mua nhiều, hiệu suất nhân
  viên, tỷ lệ huỷ, hoàn tiền, **lượt xem & chuyển đổi**, hàng sắp hết, **hàng chậm bán** (còn tồn mà 30 ngày không bán, vốn tồn), xuất Excel nhiều sheet.
- **Quản trị cửa hàng:** thông tin, logo, nhân sự (chủ tạo quản lý / nhân viên, khoá thay vì xoá), tuỳ chọn thông báo.

### Quản trị (ADMIN)
- Duyệt / từ chối hồ sơ mở cửa hàng (kèm lý do), **đình chỉ / mở lại** cửa hàng vi phạm.
- Tài khoản: tìm theo tên đăng nhập / họ tên / email / SĐT, lọc vai trò, khoá / mở khoá.
- Danh mục dùng chung, **banner trang chủ**, **báo cáo vi phạm** (xử lý / bỏ qua kèm ghi chú gửi khách).
- Tổng quan & báo cáo toàn sàn hoặc từng cửa hàng, **xếp hạng cửa hàng** theo doanh thu; xem mọi đơn hàng.

## Tác vụ tự động

| Tác vụ | Chu kỳ | Việc |
|---|---|---|
| `OrderAutomation` | 5 phút | Huỷ đơn VNPay chưa trả quá 30 phút (hoàn kho, hoàn xu); tự hoàn thành đơn đang giao quá 7 ngày |
| `DeliveryReminders` | 1 giờ | Nhắc khách bấm "đã nhận hàng" khi đơn đã giao 5 ngày (mỗi đơn một lần) |
| `CartReminders` | 1 giờ | Nhắc giỏ hàng không đổi quá 24 giờ (mỗi lần bỏ quên một lần) |
| Dọn dữ liệu bảo mật | 1 giờ / 15 phút | Xoá mã OTP hết hạn và token đã đăng xuất hết hạn (1 giờ); xoá bộ đếm đăng nhập sai cũ (15 phút) |

## Thông báo

Thông báo trong app + push FCM (một số kèm email). Loại thông báo (`NotificationType`):

`NEW_ORDER`, `ORDER_PLACED`, `ORDER_CONFIRMED`, `ORDER_SHIPPING`, `ORDER_CANCELLED`, `ORDER_COMPLETED`, `ORDER_DELIVERY_CHECK`, `PAYMENT_SUCCESS`, `PAYMENT_FAILED`, `STORE_REGISTERED`, `STORE_APPROVED`, `STORE_REJECTED`, `STORE_SUSPENDED`, `STORE_REINSTATED`, `LOW_STOCK`, `RETURN_REQUESTED`, `RETURN_UPDATED`, `REVIEW_NEW`, `REVIEW_REPLIED`, `QUESTION_NEW`, `QUESTION_ANSWERED`, `BACK_IN_STOCK`, `PRICE_DROP`, `NEW_PRODUCT`, `TIER_UP`, `CART_REMINDER`, `PRODUCT_REPORTED`, `REPORT_HANDLED`.

Khách tắt được các nhóm tiếp thị (giảm giá, sản phẩm mới, có hàng lại, cửa hàng trả lời, nhắc giỏ hàng); cửa hàng tắt được nhóm đánh giá / câu hỏi mới. Tin đơn hàng, thanh toán, trả hàng luôn gửi.

## Dữ liệu (41 bảng chính)

| Nhóm | Thực thể |
|---|---|
| Tài khoản & bảo mật | User, Customer, Address, DeviceToken, InvalidatedToken, PasswordResetCode |
| Cửa hàng | Store, StoreFollow, Supplier, QuickReply |
| Sản phẩm & kho | Category, Product, ProductImage, ProductVariant, ProductVariantImage, Inventory, StockMovement, PurchaseOrder, PurchaseOrderItem, ProductViewDay |
| Bán hàng | Cart, CartItem, Order, OrderItem, OrderStatusHistory, Payment, Promotion, PromotionProduct, ReturnRequest, ReturnItem, CoinTransaction, DailyCheckIn |
| Tương tác | ProductReview, ReviewVote, ProductQuestion, WishlistItem, Conversation, ChatMessage, Notification, HomeBanner, ViolationReport |

## Kiểm chứng

- BE: unit test JUnit + Mockito (`mvn test`), bộ Postman chạy tuần tự hết một vòng nghiệp vụ (`postman/build_collection.py
  --run <baseUrl>`).
- FE: `flutter analyze` sạch, widget test dựng cả màn bằng repository giả, **test hợp đồng** gọi BE thật
  (`test/contract/api_contract_test.dart`, bật bằng `--dart-define=ELSA_CONTRACT=true`).
- CI GitHub Actions cho cả hai repo.

## Phụ lục: danh sách API (239 endpoint)

Đường dẫn tương đối với `/elsa-store/api`. Quyền từng endpoint xem README BE (phần Vai trò và phân quyền) và Postman.

**Address**

- `POST /addresses`
- `PUT /addresses/{id}`
- `DELETE /addresses/{id}`
- `GET /addresses/me`
- `GET /addresses/{id}`
- `GET /addresses/customer/{customerId}`

**Ai**

- `GET /ai/status`
- `POST /ai/ask`
- `POST /ai/conversations/{id}/suggest-reply`
- `POST /ai/report-insights`
- `POST /ai/product-description`
- `POST /ai/reviews/{id}/suggest-reply`
- `POST /ai/questions/{id}/suggest-answer`
- `GET /ai/products/{productId}/review-digest`
- `GET /ai/store-settings`
- `PUT /ai/store-settings`

**AppLink**

- `GET /app-links/{target}`
- `GET /app-links/{target}/{id}`

**Authentication**

- `POST /auth/login`
- `POST /auth/password/forgot`
- `POST /auth/password/verify`
- `POST /auth/password/reset`
- `POST /auth/introspect`
- `POST /auth/logout`
- `POST /auth/refreshToken`

**Cart**

- `GET /cart`
- `POST /cart/items`
- `PUT /cart/items/{cartItemId}`
- `DELETE /cart/items/{cartItemId}`
- `DELETE /cart`

**Category**

- `POST /categories`
- `PUT /categories/{id}`
- `DELETE /categories/{id}`
- `GET /categories/{id}`
- `GET /categories`

**Chat**

- `POST /conversations`
- `GET /conversations/me`
- `GET /conversations/inbox`
- `GET /conversations/unread-count`
- `GET /conversations/{id}`
- `GET /conversations/{id}/messages`
- `POST /conversations/{id}/messages`
- `POST /conversations/{id}/images`
- `PUT /conversations/{id}/read`

**Coin**

- `GET /coins/me`
- `GET /coins/check-in`
- `POST /coins/check-in`

**Customer**

- `POST /customers`
- `GET /customers/me`
- `PUT /customers/me`
- `GET /customers/search`
- `PUT /customers/{id}`
- `DELETE /customers/{id}`
- `GET /customers/{id}`
- `GET /customers`

**HomeBanner**

- `GET /home/banners`
- `GET /banners`
- `POST /banners`
- `PUT /banners/{id}`
- `POST /banners/{id}/image`
- `DELETE /banners/{id}`

**Home**

- `GET /home/for-you`
- `GET /home/highlights`

**Inventory**

- `POST /inventories`
- `PUT /inventories/{id}`
- `DELETE /inventories/{id}`
- `GET /inventories/{id}`
- `GET /inventories`
- `GET /inventories/stock`
- `GET /inventories/overview`
- `PUT /inventories/variants/{variantId}`
- `DELETE /inventories/variants/{variantId}`
- `GET /inventories/variants/{variantId}/movements`

**Notification**

- `GET /notifications`
- `GET /notifications/unread-count`
- `PUT /notifications/{id}/read`
- `PUT /notifications/read-all`
- `POST /notifications/devices`
- `POST /notifications/devices/test`
- `GET /notifications/settings`
- `PUT /notifications/settings`
- `DELETE /notifications/devices`

**Order**

- `POST /orders`
- `POST /orders/quote`
- `GET /orders/{id}`
- `PUT /orders/{orderId}/status`
- `PUT /orders/{orderId}/tracking`
- `PUT /orders/{orderId}/payment-status`
- `GET /orders/page`
- `GET /orders/export`
- `GET /orders/counts`
- `GET /orders`
- `GET /orders/by-user/{userId}`
- `DELETE /orders/{id}`

**Payment**

- `POST /payment`
- `PUT /payment/{id}`
- `DELETE /payment/{id}`
- `GET /payment/{id}`
- `GET /payment`
- `GET /payment/vn-pay`
- `GET /payment/vn-pay-callback`
- `GET /payment/vnpay-ipn`

**Product**

- `POST /products`
- `POST /products`
- `PUT /products/{id}`
- `DELETE /products/{id}`
- `GET /products/{id}`
- `GET /products/mine`
- `GET /products`
- `POST /products/{id}/images`
- `GET /products/{id}/bought-together`
- `GET /products/{id}/related`
- `GET /products/{id}/images`
- `DELETE /products/{id}/images/{imageId}`
- `PUT /products/{id}/images/{imageId}/cover`
- `POST /products/search`

**ProductQuestion**

- `GET /products/{productId}/questions`
- `POST /products/{productId}/questions`
- `DELETE /questions/{id}`
- `GET /questions/store`
- `PUT /questions/{id}/answer`

**ProductReview**

- `GET /products/{productId}/reviews`
- `POST /reviews/{id}/helpful`
- `DELETE /reviews/{id}/helpful`
- `GET /products/{productId}/reviews/summary`
- `GET /products/{productId}/reviews/me`
- `POST /products/{productId}/reviews`
- `POST /products/{productId}/reviews/me/images`
- `DELETE /products/{productId}/reviews/me/images`
- `DELETE /reviews/{id}`
- `GET /reviews/store`
- `PUT /reviews/{id}/reply`

**ProductVariant**

- `POST /product-variants`
- `POST /product-variants`
- `PUT /product-variants/{id}`
- `DELETE /product-variants/{id}`
- `GET /product-variants/{id}`
- `GET /product-variants`
- `POST /product-variants/{id}/images`

**ProductImport**

- `GET /products/import/template`
- `POST /products/import`

**Promotion**

- `POST /promotions`
- `PUT /promotions/{id}`
- `DELETE /promotions/{id}`
- `GET /promotions/{id}`
- `GET /promotions`
- `PUT /promotions/{id}/products`
- `GET /promotions/coupons`

**PromotionProduct**

- `GET /promotion-products`
- `POST /promotion-products`
- `DELETE /promotion-products/{id}`

**PurchaseOrder**

- `GET /purchase-orders`
- `GET /purchase-orders/{id}`
- `POST /purchase-orders`
- `PUT /purchase-orders/{id}`
- `POST /purchase-orders/{id}/receive`
- `POST /purchase-orders/{id}/cancel`
- `DELETE /purchase-orders/{id}`

**QuickReply**

- `GET /chat/quick-replies`
- `POST /chat/quick-replies`
- `PUT /chat/quick-replies/{id}`
- `DELETE /chat/quick-replies/{id}`

**Referral**

- `GET /referrals/me`
- `POST /referrals/apply`

**Report**

- `GET /reports/dashboard`
- `GET /reports/summary`
- `GET /reports/revenue`
- `GET /reports/top-products`
- `GET /reports/revenue-by-category`
- `GET /reports/top-customers`
- `GET /reports/staff-performance`
- `GET /reports/cancellation-rate`
- `GET /reports/breakdown`
- `GET /reports/product-views`
- `PUT /reports/revenue-target`
- `GET /reports/sales-by-time`
- `GET /reports/stores`
- `GET /reports/low-stock`
- `GET /reports/slow-movers`
- `GET /reports/export`

**Return**

- `POST /returns`
- `GET /returns/me`
- `GET /returns`
- `GET /returns/{id}`
- `PUT /returns/{id}/cancel`
- `PUT /returns/{id}/approve`
- `PUT /returns/{id}/reject`
- `PUT /returns/{id}/refund`

**Revenue**

- `GET /revenues/summary`
- `GET /revenues/timeseries`
- `GET /revenues/top-products`
- `GET /revenues/dashboard`

**Search**

- `GET /search/suggestions`

**Store**

- `GET /stores`
- `GET /stores/me`
- `PUT /stores/me`
- `PUT /stores/me/pause`
- `POST /stores/me/image`
- `DELETE /stores/me/image`
- `PUT /stores/me/shipping`
- `POST /stores/{id}/follow`
- `DELETE /stores/{id}/follow`
- `GET /stores/following`
- `GET /stores/{id}`

**StoreMember**

- `POST /store-members`
- `GET /store-members`
- `PUT /store-members/{id}`
- `DELETE /store-members/{id}`

**StoreRegistration**

- `POST /store-registrations`
- `GET /store-registrations/me`
- `PUT /store-registrations/me`
- `GET /store-registrations`
- `GET /store-registrations/{id}`
- `PUT /store-registrations/{id}/approve`
- `PUT /store-registrations/{id}/reject`
- `PUT /store-registrations/{id}/suspend`
- `PUT /store-registrations/{id}/reinstate`

**Supplier**

- `POST /suppliers`
- `PUT /suppliers/{id}`
- `DELETE /suppliers/{id}`
- `GET /suppliers/{id}`
- `GET /suppliers`

**User**

- `POST /users`
- `PUT /users/{id}`
- `PUT /users/me/password`
- `DELETE /users/{id}`
- `POST /users/me/avatar`
- `DELETE /users/me/avatar`
- `GET /users/me`
- `POST /users/me/delete`
- `GET /users/{id}`
- `GET /users`

**ViolationReport**

- `POST /products/{productId}/violations`
- `GET /violations`
- `PUT /violations/{id}`

**Wishlist**

- `GET /wishlist`
- `GET /wishlist/ids`
- `POST /wishlist/{productId}`
- `DELETE /wishlist/{productId}`
