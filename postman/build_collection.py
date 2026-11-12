"""Sinh Postman collection cho elsa_store và (tuỳ chọn) chạy thử chính các request đó trên BE.

Thêm / sửa request ở danh sách step(...) bên dưới rồi sinh lại file thay vì sửa tay file JSON:

python postman/build_collection.py postman/elsa_store.postman_collection.json
python postman/build_collection.py postman/elsa_store.postman_collection.json --run http://localhost:8080/elsa-store/api

--run cần thư viện requests và một BE đang chạy (nên dùng CSDL thử: mỗi lần chạy tạo cửa hàng, khách, đơn mới).
"""
import json
import re
import sys
import time

# Mỗi bước: (thư mục, tên, method, path, token, body, mong đợi, lưu biến)
#   body: dict JSON | ("form", dict JSON cho phần "data") | None
#   lưu biến: list (tên biến, biểu thức JS trên `j` (JSON phản hồi), hàm Python trên dict j)
S = []


def step(folder, name, method, path, token=None, body=None, expect=200, saves=(), note=None, query=None):
    S.append(dict(folder=folder, name=name, method=method, path=path, token=token, body=body, expect=expect,
                  saves=list(saves), note=note, query=query or {}))


F1 = "1. Quản trị & mở cửa hàng"
F2 = "2. Sản phẩm, kho, nhập hàng, khuyến mãi"
F3 = "3. Khách hàng: mua hàng, đánh giá, yêu thích"
F4 = "4. Báo cáo & doanh thu"
F5 = "5. Kiểm tra phân quyền"
F6 = "6. Mật khẩu"

step(F1, "Đăng nhập ADMIN", "POST", "/auth/login", body={"username": "admin123", "password": "admin123"},
     saves=[("adminToken", "j.data.accessToken", lambda j: j["data"]["accessToken"])],
     note="Tài khoản ADMIN mặc định tạo khi BE khởi động. Request đầu tiên cũng sinh runSuffix để tên không trùng giữa các lần chạy.")
step(F1, "Đăng ký mở cửa hàng (công khai)", "POST", "/store-registrations",
     body={"username": "own{{runSuffix}}", "password": "123456", "storeName": "Elsa Demo {{runSuffix}}",
           "description": "Cửa hàng thử Postman", "ownerName": "Chủ Demo", "email": "own{{runSuffix}}@example.com",
           "phone": "0911000000", "address": "1 Nguyễn Huệ, Q1"})
step(F1, "ADMIN: hồ sơ chờ duyệt", "GET", "/store-registrations", token="adminToken",
     query={"status": "PENDING", "size": "50"},
     saves=[("storeId", "j.data.content.find(s => s.name === 'Elsa Demo ' + pm.collectionVariables.get('runSuffix')).id",
             lambda j, v: [s for s in j["data"]["content"] if s["name"] == "Elsa Demo " + v["runSuffix"]][0]["id"])])
step(F1, "ADMIN: duyệt cửa hàng", "PUT", "/store-registrations/{{storeId}}/approve", token="adminToken")
step(F1, "ADMIN: thêm banner trang chủ", "POST", "/banners", token="adminToken",
     body={"title": "Sale cuối tuần {{runSuffix}}", "subtitle": "Giày chạy giảm đến 30%", "linkType": "SEARCH",
           "linkValue": "giày chạy", "sortOrder": 1},
     saves=[("bannerId", "j.data.id", lambda j: j["data"]["id"])],
     note="linkType: NONE, PRODUCT, STORE, CATEGORY (linkValue = id) hoặc SEARCH (từ khoá). Ảnh: POST /banners/{id}/image (multipart files).")
step(F1, "Banner trang chủ đang hiện (công khai)", "GET", "/home/banners")
step(F1, "ADMIN: xoá banner", "DELETE", "/banners/{{bannerId}}", token="adminToken")
step(F1, "ADMIN: đình chỉ cửa hàng (thử)", "PUT", "/store-registrations/{{storeId}}/suspend", token="adminToken",
     body={"reason": "Kiểm tra đình chỉ"},
     note="Cửa hàng bị ẩn khỏi sàn, khách không đặt đơn mới; request sau mở lại ngay để các bước tiếp theo chạy được.")
step(F1, "ADMIN: mở lại cửa hàng", "PUT", "/store-registrations/{{storeId}}/reinstate", token="adminToken")
step(F1, "Chủ cửa hàng đăng nhập", "POST", "/auth/login", body={"username": "own{{runSuffix}}", "password": "123456"},
     saves=[("ownerToken", "j.data.accessToken", lambda j: j["data"]["accessToken"])])
step(F1, "Chủ tạo nhân viên", "POST", "/store-members", token="ownerToken",
     body={"username": "staff{{runSuffix}}", "password": "123456", "fullName": "Nhân viên Demo", "role": "STAFF"})
step(F1, "Nhân viên đăng nhập", "POST", "/auth/login", body={"username": "staff{{runSuffix}}", "password": "123456"},
     saves=[("staffToken", "j.data.accessToken", lambda j: j["data"]["accessToken"])])
step(F1, "ADMIN: tạo danh mục", "POST", "/categories", token="adminToken", body={"name": "Giày {{runSuffix}}"},
     saves=[("categoryId", "j.data.id", lambda j: j["data"]["id"])])

step(F2, "Tạo sản phẩm (multipart: phần 'data' là JSON)", "POST", "/products", token="ownerToken",
     body=("form", {"name": "Giày chạy bộ {{runSuffix}}", "description": "Giày thử", "basePrice": 1000000, "status": 1,
                    "categoryId": "{{categoryId}}"}),
     saves=[("productId", "j.data.id", lambda j: j["data"]["id"])])
step(F2, "Tạo phân loại (biến thể)", "POST", "/product-variants", token="ownerToken",
     body=("form", {"productId": "{{productId}}", "color": "Đen", "size": "42", "price": 1000000,
                    "sku": "SKU-{{runSuffix}}", "status": 1}),
     saves=[("variantId", "j.data.id", lambda j: j["data"]["id"])])
step(F2, "Bộ ảnh sản phẩm (tải ảnh: POST /products/{id}/images, multipart 'files')", "GET",
     "/products/{{productId}}/images", token="staffToken")
step(F2, "AI viết mô tả sản phẩm (bản nháp)", "POST", "/ai/product-description", token="staffToken",
     body={"name": "Giày chạy bộ {{runSuffix}}", "productId": "{{productId}}", "categoryId": "{{categoryId}}",
           "notes": "êm, nhẹ, hợp chạy sáng"},
     note="Lấy thêm màu / cỡ của các phân loại. Chưa bật AI hoặc AI bận: ai=false, trả mẫu soạn sẵn kèm notice.")
step(F2, "Kiểm kê: bắt đầu theo dõi kho + giá vốn", "PUT", "/inventories/variants/{{variantId}}", token="ownerToken",
     body={"quantity": 5, "minQuantity": 2, "costPrice": 600000, "note": "Kiểm kê đầu kỳ"})
step(F2, "Tạo nhà cung cấp", "POST", "/suppliers", token="ownerToken",
     body={"name": "NCC Demo {{runSuffix}}", "phone": "0281234567"},
     saves=[("supplierId", "j.data.id", lambda j: j["data"]["id"])])
step(F2, "Tạo phiếu nhập nháp", "POST", "/purchase-orders", token="ownerToken",
     body={"supplierId": "{{supplierId}}", "note": "Đợt 1",
           "items": [{"productVariantId": "{{variantId}}", "quantity": 10, "unitPrice": 700000}]},
     saves=[("purchaseOrderId", "j.data.id", lambda j: j["data"]["id"])])
step(F2, "Nhập kho phiếu nhập (cộng kho, tính giá vốn bình quân)", "POST", "/purchase-orders/{{purchaseOrderId}}/receive",
     token="ownerToken")
step(F2, "Nhập kho lần 2 bị chặn (1025)", "POST", "/purchase-orders/{{purchaseOrderId}}/receive", token="ownerToken",
     expect=409)
step(F2, "Tệp Excel mẫu để nhập sản phẩm hàng loạt", "GET", "/products/import/template", token="ownerToken",
     note="Nhập: POST /products/import (multipart, phần file = .xlsx; dryRun=true để xem trước). Có lỗi ở bất kỳ dòng "
          "nào thì không tạo gì và trả danh sách lỗi theo số dòng.")
step(F2, "Danh sách kho", "GET", "/inventories/stock", token="ownerToken", query={"filter": "ALL", "size": "20"})
step(F2, "Tổng quan kho", "GET", "/inventories/overview", token="ownerToken")
step(F2, "Thẻ kho của phân loại", "GET", "/inventories/variants/{{variantId}}/movements", token="ownerToken")
step(F2, "Khuyến mãi giảm 10% cho sản phẩm (gắn sản phẩm cùng lúc)", "POST", "/promotions", token="ownerToken",
     body={"name": "Giảm 10%", "type": 2, "value": 10, "status": 1, "productIds": ["{{productId}}"]},
     saves=[("promotionId", "j.data.id", lambda j: j["data"]["id"])])
step(F2, "Mã giảm giá 50.000đ cho đơn từ 500.000đ", "POST", "/promotions", token="ownerToken",
     body={"name": "Mã 50K", "type": 1, "value": 50000, "status": 1, "couponCode": "GIAM{{runSuffix}}",
           "minOrderValue": 500000})
step(F2, "Bật tạm nghỉ bán (khách không đặt được đơn)", "PUT", "/stores/me/pause", token="ownerToken",
     body={"paused": True, "note": "Nghỉ lễ đến thứ Hai"})
step(F2, "Tắt tạm nghỉ bán", "PUT", "/stores/me/pause", token="ownerToken", body={"paused": False})
step(F2, "Phí giao 30.000đ, miễn phí cho đơn từ 2.000.000đ", "PUT", "/stores/me/shipping", token="ownerToken",
     body={"shippingFee": 30000, "freeShippingThreshold": 2000000},
     note="Phí 0 = giao miễn phí. Đơn tại quầy không tính phí giao.")

step(F3, "Khách đăng ký tài khoản", "POST", "/users",
     body={"username": "khach{{runSuffix}}", "password": "123456", "email": "khach{{runSuffix}}@example.com",
           "fullName": "Khách Demo", "phone": "0900000099"})
step(F3, "Khách đăng nhập", "POST", "/auth/login", body={"username": "khach{{runSuffix}}", "password": "123456"},
     saves=[("customerToken", "j.data.accessToken", lambda j: j["data"]["accessToken"])])
step(F3, "Trang chủ: đang giảm giá, bán chạy (công khai)", "GET", "/home/highlights", query={"size": "10"})
step(F3, "Gợi ý khi gõ ô tìm kiếm (công khai)", "GET", "/search/suggestions", query={"keyword": "giay"})
step(F3, "Tìm sản phẩm (công khai)", "POST", "/products/search", body={"keyword": "giày", "page": 0, "sizePage": 10})
step(F3, "Chi tiết sản phẩm (giá khuyến mãi, điểm đánh giá)", "GET", "/products/{{productId}}")
step(F3, "Sản phẩm tương tự (công khai)", "GET", "/products/{{productId}}/related", query={"size": "8"})
step(F3, "Gợi ý cho bạn (theo sản phẩm vừa xem / thả tim / đã mua)", "GET", "/home/for-you", token="customerToken",
     query={"recent": "{{productId}}", "size": "10"})
step(F3, "Khách mua sản phẩm này cũng mua (công khai)", "GET", "/products/{{productId}}/bought-together",
     query={"size": "8"})
step(F3, "Thêm địa chỉ giao hàng", "POST", "/addresses", token="customerToken",
     body={"fullName": "Khách Demo", "phone": "0900000099", "detail": "12 Lê Lợi", "district": "Quận 1",
           "province": "TP HCM", "isDefault": True},
     saves=[("addressId", "j.data.id", lambda j: j["data"]["id"])])
step(F3, "Báo giá trước khi đặt (kèm mã giảm giá, phí giao)", "POST", "/orders/quote", token="customerToken",
     body={"items": [{"productVariantId": "{{variantId}}", "quantity": 1}], "couponCode": "GIAM{{runSuffix}}"})
step(F3, "Ví xu thưởng của khách (số dư, quy tắc, lịch sử)", "GET", "/coins/me", token="customerToken")
step(F3, "Điểm danh: trạng thái chuỗi 7 ngày", "GET", "/coins/check-in", token="customerToken")
step(F3, "Điểm danh hôm nay (+50 xu ngày 1)", "POST", "/coins/check-in", token="customerToken",
     note="Chuỗi 7 ngày: 50, 50, 100, 100, 150, 150, 500 xu; bỏ lỡ một ngày thì về ngày 1.")
step(F3, "Điểm danh lần hai trong ngày bị chặn (1030)", "POST", "/coins/check-in", token="customerToken", expect=409)
step(F3, "Mã giới thiệu của tôi (sinh lần đầu)", "GET", "/referrals/me", token="customerToken",
     note="Bạn nhập mã khi đăng ký (POST /users có referralCode) hoặc trong 7 ngày đầu; đơn đầu tiên từ 100.000đ hoàn "
          "thành thì người mời +5.000 xu, người được mời +2.000 xu.")
step(F3, "Nhập mã giới thiệu sai bị chặn (1031)", "POST", "/referrals/apply", token="customerToken",
     body={"code": "ZZZZZZ"}, expect=400)
step(F3, "Báo giá có dùng xu (useCoins; ví trống thì dùng 0 xu)", "POST", "/orders/quote", token="customerToken",
     body={"items": [{"productVariantId": "{{variantId}}", "quantity": 1}], "useCoins": True})
step(F3, "Đặt hàng COD", "POST", "/orders", token="customerToken",
     body={"addressId": "{{addressId}}", "couponCode": "GIAM{{runSuffix}}", "note": "Giao giờ hành chính",
           "items": [{"productVariantId": "{{variantId}}", "quantity": 1}]},
     saves=[("orderId", "j.data.id", lambda j: j["data"]["id"])])
step(F3, "Chưa nhận hàng thì chưa đánh giá được", "GET", "/products/{{productId}}/reviews/me", token="customerToken")
step(F3, "Nhân viên xác nhận đơn", "PUT", "/orders/{{orderId}}/status", token="staffToken",
     query={"status": "DA_XAC_NHAN"})
step(F3, "Nhân viên giao cho đơn vị vận chuyển (kèm mã vận đơn)", "PUT", "/orders/{{orderId}}/status", token="staffToken",
     query={"status": "DANG_GIAO", "carrier": "GHN", "trackingCode": "GHN{{runSuffix}}"},
     note="carrier / trackingCode tuỳ chọn, chỉ nhận khi chuyển sang DANG_GIAO; mã chỉ gồm chữ không dấu, số và - _ . /.")
step(F3, "Sửa mã vận đơn khi đang giao (khách nhận thông báo mã mới)", "PUT", "/orders/{{orderId}}/tracking",
     token="staffToken", body={"carrier": "Giao Hàng Nhanh", "trackingCode": "GHN{{runSuffix}}X"})
step(F3, "Khách bấm Đã nhận được hàng (hoàn thành, ghi nhận doanh thu)", "PUT", "/orders/{{orderId}}/status",
     token="customerToken", query={"status": "HOAN_THANH"})
step(F3, "Chi tiết đơn kèm dòng thời gian", "GET", "/orders/{{orderId}}", token="customerToken")
step(F3, "Cửa hàng: danh sách đơn phân trang, tìm theo SĐT", "GET", "/orders/page", token="staffToken",
     query={"keyword": "0900000099", "page": "0", "size": "20"})
step(F3, "Cửa hàng: số đơn theo trạng thái", "GET", "/orders/counts", token="staffToken")
step(F3, "Đơn thứ hai: khách đặt rồi tự huỷ kèm lý do", "POST", "/orders", token="customerToken",
     body={"addressId": "{{addressId}}", "items": [{"productVariantId": "{{variantId}}", "quantity": 1}]},
     saves=[("cancelOrderId", "j.data.id", lambda j: j["data"]["id"])])
step(F3, "Khách huỷ đơn chưa xác nhận (lý do gửi cho cửa hàng)", "PUT", "/orders/{{cancelOrderId}}/status",
     token="customerToken", query={"status": "DA_HUY", "reason": "Đặt nhầm size"})
step(F3, "Khách viết đánh giá 5 sao", "POST", "/products/{{productId}}/reviews", token="customerToken",
     body={"rating": 5, "comment": "Giày êm, đúng size"},
     saves=[("reviewId", "j.data.id", lambda j: j["data"]["id"])])
step(F3, "Tổng quan đánh giá (công khai)", "GET", "/products/{{productId}}/reviews/summary")
step(F3, "Danh sách đánh giá (công khai)", "GET", "/products/{{productId}}/reviews", query={"page": "0", "size": "10"})
step(F3, "Đánh giá hữu ích nhất trước (công khai)", "GET", "/products/{{productId}}/reviews",
     query={"sort": "helpful", "page": "0", "size": "10"},
     note="Khách khác bấm POST /reviews/{id}/helpful (bỏ: DELETE); tự bấm cho đánh giá của mình bị 400.")
step(F3, "Cửa hàng: đánh giá chưa trả lời", "GET", "/reviews/store", token="staffToken", query={"unreplied": "true"})
step(F3, "Cửa hàng trả lời đánh giá", "PUT", "/reviews/{{reviewId}}/reply", token="staffToken",
     body={"reply": "Cảm ơn bạn đã ủng hộ Elsa!"})
step(F3, "Khách hỏi về sản phẩm", "POST", "/products/{{productId}}/questions", token="customerToken",
     body={"question": "Giày có size 45 không?"},
     saves=[("questionId", "j.data.id", lambda j: j["data"]["id"])],
     note="Mỗi khách treo tối đa 3 câu chưa được trả lời cho một sản phẩm (quá thì 429, mã 1029).")
step(F3, "Cửa hàng: câu hỏi chưa trả lời", "GET", "/questions/store", token="staffToken", query={"unanswered": "true"})
step(F3, "Cửa hàng trả lời câu hỏi", "PUT", "/questions/{{questionId}}/answer", token="staffToken",
     body={"answer": "Dạ có size 45, bạn đặt nhé!"})
step(F3, "Hỏi đáp của sản phẩm (công khai, chỉ câu đã trả lời)", "GET", "/products/{{productId}}/questions",
     query={"page": "0", "size": "10"})
step(F3, "Cửa hàng: thêm câu trả lời nhanh cho chat", "POST", "/chat/quick-replies", token="staffToken",
     body={"title": "Chào khách", "content": "Dạ Elsa chào bạn, bạn cần tư vấn size nào ạ?"})
step(F3, "Cửa hàng: danh sách câu trả lời nhanh", "GET", "/chat/quick-replies", token="ownerToken")
step(F3, "Khách báo cáo sản phẩm vi phạm", "POST", "/products/{{productId}}/violations", token="customerToken",
     body={"reason": "WRONG_DESCRIPTION", "note": "Màu thực tế khác ảnh"},
     saves=[("violationId", "j.data.id", lambda j: j["data"]["id"])],
     note="reason: FAKE, WRONG_DESCRIPTION, SCAM, PROHIBITED, OTHER. Đang chờ xử lý thì báo lại cùng sản phẩm bị 409.")
step(F3, "ADMIN: báo cáo vi phạm chờ xử lý", "GET", "/violations", token="adminToken", query={"status": "PENDING"})
step(F3, "ADMIN: bỏ qua báo cáo (kèm ghi chú gửi khách)", "PUT", "/violations/{{violationId}}", token="adminToken",
     body={"status": "DISMISSED", "note": "Ảnh đúng với sản phẩm"})
step(F3, "Khách xem tuỳ chọn thông báo", "GET", "/notifications/settings", token="customerToken",
     note="Nhóm tắt được: PRICE_DROP, NEW_PRODUCT, BACK_IN_STOCK, STORE_REPLIES (khách); CUSTOMER_FEEDBACK (cửa hàng).")
step(F3, "Khách tắt thông báo sản phẩm mới", "PUT", "/notifications/settings", token="customerToken",
     body={"groups": {"NEW_PRODUCT": False}})
step(F3, "Khách gửi yêu cầu trả 1 sản phẩm", "POST", "/returns", token="customerToken",
     body={"orderId": "{{orderId}}", "reason": "Sai kích cỡ", "note": "Chật quá",
           "items": [{"productVariantId": "{{variantId}}", "quantity": 1}]},
     saves=[("returnId", "j.data.id", lambda j: j["data"]["id"])],
     note="Chỉ đơn đã hoàn thành, trong 7 ngày; suggestedRefund = giá khách đã trả cho món đó.")
step(F3, "Cửa hàng: yêu cầu trả hàng chờ duyệt", "GET", "/returns", token="staffToken", query={"status": "CHO_DUYET"})
step(F3, "Nhân viên duyệt yêu cầu trả hàng", "PUT", "/returns/{{returnId}}/approve", token="staffToken",
     body={"note": "Bạn gửi hàng về 1 Nguyễn Huệ, Q1 nhé"})
step(F3, "Chủ xác nhận nhận hàng và hoàn tiền (hàng về kho)", "PUT", "/returns/{{returnId}}/refund", token="ownerToken",
     body={"note": "Đã chuyển khoản"})
step(F3, "Khách xem yêu cầu trả hàng của đơn", "GET", "/returns/me", token="customerToken",
     query={"orderId": "{{orderId}}"})
step(F3, "Thả tim sản phẩm", "POST", "/wishlist/{{productId}}", token="customerToken")
step(F3, "Danh sách yêu thích", "GET", "/wishlist", token="customerToken")
step(F3, "Bỏ tim", "DELETE", "/wishlist/{{productId}}", token="customerToken")
step(F3, "Theo dõi cửa hàng (báo khi có sản phẩm mới)", "POST", "/stores/{{storeId}}/follow", token="customerToken")
step(F3, "Cửa hàng đang theo dõi", "GET", "/stores/following", token="customerToken")
step(F3, "Đơn VNPay: tạo đơn trả online", "POST", "/orders", token="customerToken",
     body={"addressId": "{{addressId}}", "paymentMethod": 1, "items": [{"productVariantId": "{{variantId}}", "quantity": 1}]},
     saves=[("vnpayOrderId", "j.data.id", lambda j: j["data"]["id"])])
step(F3, "Đơn VNPay: lấy link thanh toán sandbox", "GET", "/payment/vn-pay", token="customerToken",
     query={"orderId": "{{vnpayOrderId}}"},
     note="Mở data.paymentUrl trên trình duyệt, thẻ thử NCB 9704198526191432198 / NGUYEN VAN A / 07/15 / OTP 123456.")

step(F4, "Tổng quan (dashboard)", "GET", "/reports/dashboard", token="ownerToken")
step(F4, "Tổng hợp kỳ + so với kỳ trước + lợi nhuận", "GET", "/reports/summary", token="ownerToken",
     query={"from": "{{today}}", "to": "{{today}}"})
step(F4, "Doanh thu theo kênh bán / cách trả tiền", "GET", "/reports/breakdown", token="ownerToken",
     query={"from": "{{today}}", "to": "{{today}}"})
step(F4, "Doanh thu theo ngày", "GET", "/reports/revenue", token="ownerToken",
     query={"from": "{{today}}", "to": "{{today}}", "groupBy": "DAY"})
step(F4, "Sản phẩm bán chạy (kèm lãi gộp)", "GET", "/reports/top-products", token="ownerToken",
     query={"from": "{{today}}", "to": "{{today}}"})
step(F4, "Lượt xem sản phẩm và tỉ lệ chuyển đổi", "GET", "/reports/product-views", token="ownerToken",
     query={"from": "{{today}}", "to": "{{today}}", "limit": "10"},
     note="Lượt xem đếm khi khách / người chưa đăng nhập mở GET /products/{id}; người của cửa hàng và ADMIN không tính.")
step(F4, "Đặt mục tiêu doanh thu tháng (trả tổng quan có dự báo)", "PUT", "/reports/revenue-target", token="ownerToken",
     body={"target": 50000000},
     note="target trống hoặc 0 = bỏ mục tiêu. Tổng quan có monthTarget, monthForecast, daysLeftInMonth, neededPerDay.")
step(F4, "Giờ vàng: đơn theo thứ và giờ đặt hàng", "GET", "/reports/sales-by-time", token="ownerToken",
     query={"from": "{{today}}", "to": "{{today}}"},
     note="dayOfWeek theo ISO (1 = Thứ hai … 7 = Chủ nhật); đơn đặt trong kỳ trừ đơn huỷ.")
step(F4, "Hàng chậm bán (còn tồn, 30 ngày không bán)", "GET", "/reports/slow-movers", token="ownerToken",
     query={"days": "30", "limit": "20"},
     note="Còn tồn kho, theo dõi kho từ trước mốc và không có đơn (trừ huỷ) từ mốc; vốn tồn (tồn × giá vốn) nhiều nhất trước.")
step(F4, "AI nhận xét báo cáo kỳ (tóm tắt, nổi bật, việc nên làm)", "POST", "/ai/report-insights", token="ownerToken",
     body={"from": "{{today}}", "to": "{{today}}"},
     note="Mô hình chỉ thấy bảng số thật của kỳ. Chưa bật AI / AI bận / hỏi quá 10 lần mỗi 10 phút: ai=false, nhận xét theo quy tắc kèm notice.")
step(F4, "ADMIN xem báo cáo một cửa hàng", "GET", "/reports/summary", token="adminToken",
     query={"from": "{{today}}", "to": "{{today}}", "storeId": "{{storeId}}"})
step(F4, "ADMIN: xếp hạng cửa hàng theo doanh thu", "GET", "/reports/stores", token="adminToken",
     query={"from": "{{today}}", "to": "{{today}}"})
step(F4, "Xuất Excel", "GET", "/reports/export", token="ownerToken", query={"from": "{{today}}", "to": "{{today}}"},
     note="Postman: Send and Download để lưu tệp .xlsx.")

step(F5, "Nhân viên không xem được báo cáo", "GET", "/reports/dashboard", token="staffToken", expect=403)
step(F5, "Nhân viên không báo cáo vi phạm", "POST", "/products/{{productId}}/violations", token="staffToken",
     body={"reason": "FAKE"}, expect=403)
step(F5, "Chủ cửa hàng không tự đình chỉ / mở lại", "PUT", "/store-registrations/{{storeId}}/reinstate",
     token="ownerToken", expect=403)
step(F5, "Chủ cửa hàng không xem bảng xếp hạng toàn sàn", "GET", "/reports/stores", token="ownerToken",
     query={"from": "{{today}}", "to": "{{today}}"}, expect=403)
step(F5, "Nhân viên không mở được kho / giá vốn", "GET", "/inventories/stock", token="staffToken", expect=403)
step(F5, "ADMIN chỉ xem, không sửa khuyến mãi", "POST", "/promotions", token="adminToken",
     body={"name": "x", "type": 2, "value": 5}, expect=403)
step(F5, "Khách chưa đăng nhập không có danh sách yêu thích", "GET", "/wishlist/ids", expect=401)
step(F5, "Nhân viên không viết đánh giá", "POST", "/products/{{productId}}/reviews", token="staffToken",
     body={"rating": 5}, expect=403)
step(F5, "Nhân viên không đặt câu hỏi", "POST", "/products/{{productId}}/questions", token="staffToken",
     body={"question": "x"}, expect=403)

step(F6, "Quên mật khẩu: gửi mã (luôn trả cùng kết quả)", "POST", "/auth/password/forgot",
     body={"identifier": "khach{{runSuffix}}"},
     note="Mã 6 số gửi qua email; máy chưa cấu hình mail thì xem log BE (WARN). Điền vào biến resetCode rồi chạy 2 request sau bằng tay.")
step(F6, "Quên mật khẩu: kiểm mã (chạy tay)", "POST", "/auth/password/verify",
     body={"identifier": "khach{{runSuffix}}", "code": "{{resetCode}}"}, expect=None)
step(F6, "Quên mật khẩu: đặt mật khẩu mới (chạy tay)", "POST", "/auth/password/reset",
     body={"identifier": "khach{{runSuffix}}", "code": "{{resetCode}}", "newPassword": "matkhaumoi"}, expect=None)
step(F6, "Đổi mật khẩu khi đang đăng nhập (trả cặp token mới)", "PUT", "/users/me/password", token="customerToken",
     body={"currentPassword": "123456", "newPassword": "doimatkhau1"},
     saves=[("customerToken", "j.data.accessToken", lambda j: j["data"]["accessToken"])])
step(F6, "Khách xoá tài khoản (bị chặn vì còn đơn VNPay đang chờ)", "POST", "/users/me/delete", token="customerToken",
     body={"password": "doimatkhau1"}, expect=400,
     note="Hết đơn đang xử lý thì trả 200: thông tin cá nhân bị ẩn danh, tài khoản khoá, mọi token hết hiệu lực.")

PRE_FIRST = [
    "if (!pm.collectionVariables.get('runSuffix')) {",
    "  pm.collectionVariables.set('runSuffix', String(Date.now()).slice(-6));",
    "}",
    "const d = new Date(Date.now() + 7 * 3600 * 1000);",
    "pm.collectionVariables.set('today', d.toISOString().slice(0, 10));",
]


def js_tests(st):
    lines = []
    if st["expect"] is not None:
        lines += ["pm.test('HTTP %d', () => pm.response.to.have.status(%d));" % (st["expect"], st["expect"])]
    if st["saves"]:
        lines += ["const j = pm.response.json();"]
        for var, expr, _ in st["saves"]:
            lines += ["pm.collectionVariables.set('%s', %s);" % (var, expr)]
    return lines


def body_text(obj, indent=None):
    # Biến id là số: bỏ ngoặc kép để Postman gửi đúng kiểu số ("{{productId}}" -> {{productId}}).
    return re.sub(r'"\{\{(\w+Id)\}\}"', r'{{\1}}', json.dumps(obj, ensure_ascii=False, indent=indent))


def build():
    folders = {}
    for i, st in enumerate(S):
        raw_path = st["path"]
        query = [{"key": k, "value": v} for k, v in st["query"].items()]
        url = {"raw": "{{baseUrl}}" + raw_path + ("?" + "&".join("%s=%s" % (k, v) for k, v in st["query"].items())
                                                   if query else ""),
               "host": ["{{baseUrl}}"], "path": [p for p in raw_path.strip("/").split("/")]}
        if query:
            url["query"] = query
        req = {"method": st["method"], "header": [], "url": url}
        if st["token"]:
            req["auth"] = {"type": "bearer", "bearer": [{"key": "token", "value": "{{%s}}" % st["token"], "type": "string"}]}
        else:
            req["auth"] = {"type": "noauth"}
        body = st["body"]
        if isinstance(body, tuple):
            req["body"] = {"mode": "formdata", "formdata": [
                {"key": "data", "value": body_text(body[1]), "type": "text",
                 "contentType": "application/json"}]}
        elif body is not None:
            req["header"].append({"key": "Content-Type", "value": "application/json"})
            req["body"] = {"mode": "raw", "raw": body_text(body, 2),
                           "options": {"raw": {"language": "json"}}}
        if st["note"]:
            req["description"] = st["note"]
        events = [{"listen": "test", "script": {"type": "text/javascript", "exec": js_tests(st)}}]
        if i == 0:
            events.insert(0, {"listen": "prerequest", "script": {"type": "text/javascript", "exec": PRE_FIRST}})
        folders.setdefault(st["folder"], []).append({"name": st["name"], "event": events, "request": req})
    variables = ["baseUrl", "runSuffix", "today", "resetCode"] + sorted(
        {v for st in S for v, _, _ in st["saves"]})
    return {
        "info": {
            "name": "Elsa Store API",
            "description": "Bộ request đầy đủ cho elsa_store: chạy tuần tự từ trên xuống (Collection Runner) — mỗi request "
                           "tự lưu id / token vào Collection Variables cho bước sau. Mỗi lần chạy sinh runSuffix mới nên "
                           "chạy lại không trùng tên. Thư mục 6 có 2 request cần mã OTP nhập tay (biến resetCode).",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "variable": [{"key": k, "value": "http://localhost:8080/elsa-store/api" if k == "baseUrl" else ""}
                     for k in variables],
        "item": [{"name": name, "item": items} for name, items in folders.items()],
    }


def run(base):
    import requests
    v = {"runSuffix": str(int(time.time() * 1000))[-6:],
         "today": time.strftime("%Y-%m-%d", time.gmtime(time.time() + 7 * 3600)),
         "resetCode": "000000"}

    def sub(x):
        if isinstance(x, str):
            m = re.fullmatch(r"\{\{(\w+)\}\}", x)
            if m:  # giá trị chỉ gồm một biến: giữ kiểu (số)
                return v[m.group(1)]
            return re.sub(r"\{\{(\w+)\}\}", lambda mm: str(v[mm.group(1)]), x)
        if isinstance(x, dict):
            return {k: sub(val) for k, val in x.items()}
        if isinstance(x, list):
            return [sub(i) for i in x]
        return x

    fails = 0
    for st in S:
        headers = {"Authorization": "Bearer " + v[st["token"]]} if st["token"] else {}
        url = base + sub(st["path"])
        params = sub(st["query"])
        body = st["body"]
        kw = {}
        if isinstance(body, tuple):
            kw["files"] = {"data": (None, json.dumps(sub(body[1])), "application/json")}
        elif body is not None:
            kw["json"] = sub(body)
        r = requests.request(st["method"], url, headers=headers, params=params, timeout=60, **kw)
        ok = st["expect"] is None or r.status_code == st["expect"]
        try:
            j = r.json()
        except ValueError:
            j = {}
        saved = []
        if ok:
            for var, _, fn in st["saves"]:
                try:
                    v[var] = fn(j, v) if fn.__code__.co_argcount == 2 else fn(j)
                    saved.append(var)
                except Exception as e:  # noqa: BLE001
                    ok = False
                    saved.append("%s!%s" % (var, e))
        print(("PASS " if ok else "FAIL ") + "[%d] %s %s" % (r.status_code, st["method"], st["path"])
              + ("" if ok else " :: " + r.text[:200]) + (" saved=" + ",".join(saved) if saved else ""))
        fails += 0 if ok else 1
    print("FAILED: %d / %d" % (fails, len(S)))


if __name__ == "__main__":
    out = sys.argv[1]
    with open(out, "w", encoding="utf-8") as f:
        json.dump(build(), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote %s (%d requests)" % (out, len(S)))
    if "--run" in sys.argv:
        run(sys.argv[sys.argv.index("--run") + 1])
