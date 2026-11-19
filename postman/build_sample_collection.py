"""Sinh Postman collection chạy trên DỮ LIỆU MẪU có sẵn (nạp từ file db.sql), khác bộ elsa_store.postman_collection.json
(bộ đó tự tạo cửa hàng / khách mới từ đầu).

Dùng các tài khoản có sẵn trong db.sql: admin123, owner123 (chủ "Elsa Store", id 1), user01 (khách). Sản phẩm / biến thể
được chọn tự động (biến thể đang theo dõi tồn kho và còn hàng) nên không phụ thuộc id cố định.

python postman/build_sample_collection.py postman/elsa_store_sample_data.postman_collection.json
python postman/build_sample_collection.py postman/elsa_store_sample_data.postman_collection.json --run http://localhost:8080/elsa-store/api ownerPassword=...

Lưu ý: chạy cả bộ sẽ GHI vào CSDL (đặt 1 đơn mới, thêm danh mục, đánh giá...). Mỗi lần chạy sinh runSuffix mới nên
chạy lại không trùng tên.
"""
import importlib.util
import json
import os
import sys

_spec = importlib.util.spec_from_file_location("bc", os.path.join(os.path.dirname(__file__), "build_collection.py"))
bc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(bc)
bc.S.clear()
step = bc.step

DEFAULTS = {
    "baseUrl": "http://localhost:8080/elsa-store/api",
    "storeId": "1",
    "adminUsername": "admin123", "adminPassword": "admin123",
    "ownerUsername": "owner123", "ownerPassword": "",
    "customerUsername": "user01", "customerPassword": "123456",
    "fromDate": "2026-01-01",
}

F0 = "0. Đăng nhập tài khoản có sẵn"
F1 = "1. Khách xem cửa hàng (không cần đăng nhập)"
F2 = "2. Khách user01 đặt hàng"
F3 = "3. Cửa hàng xử lý đơn"
F4 = "4. Quản lý cửa hàng"
F5 = "5. Báo cáo & doanh thu"
F6 = "6. Quản trị (ADMIN)"

tok = lambda name: [(name, "j.data.accessToken", lambda j: j["data"]["accessToken"])]

step(F0, "ADMIN đăng nhập", "POST", "/auth/login",
     body={"username": "{{adminUsername}}", "password": "{{adminPassword}}"}, saves=tok("adminToken"),
     note="admin123 / admin123 — BE tự tạo khi khởi động.")
step(F0, "Chủ cửa hàng owner123 đăng nhập", "POST", "/auth/login",
     body={"username": "{{ownerUsername}}", "password": "{{ownerPassword}}"}, saves=tok("ownerToken"),
     note="Điền mật khẩu của owner123 vào biến ownerPassword (tab Variables của collection) trước khi chạy.")
step(F0, "Khách user01 đăng nhập", "POST", "/auth/login",
     body={"username": "{{customerUsername}}", "password": "{{customerPassword}}"}, saves=tok("customerToken"),
     note="user01 / user02 / user03 trong db.sql đều có mật khẩu 123456.")

step(F1, "Danh mục của cửa hàng (cây 2 tầng)", "GET", "/categories", query={"storeId": "{{storeId}}"},
     saves=[("categoryId", "j.data[0].id", lambda j: j["data"][0]["id"])])
step(F1, "Thông tin cửa hàng", "GET", "/stores/{{storeId}}")
step(F1, "Banner trang chủ", "GET", "/home/banners")
step(F1, "Sản phẩm nổi bật", "GET", "/home/highlights", query={"size": "10"})
step(F1, "Gợi ý tìm kiếm", "GET", "/search/suggestions", query={"keyword": "nike"})
step(F1, "Tìm sản phẩm \"nike\"", "POST", "/products/search", body={"keyword": "nike", "page": 0, "sizePage": 10})
step(F1, "Lọc sản phẩm theo danh mục", "POST", "/products/search",
     body={"categoryId": "{{categoryId}}", "page": 0, "sizePage": 10})

step(F2, "Chủ: chọn biến thể còn hàng để đặt", "GET", "/inventories/stock", token="ownerToken",
     query={"filter": "ALL", "size": "200"},
     saves=[("variantId", "j.data.content.find(x => x.tracked && x.quantity > 0).productVariantId",
             lambda j: [x for x in j["data"]["content"] if x["tracked"] and (x["quantity"] or 0) > 0][0]["productVariantId"]),
            ("productId", "j.data.content.find(x => x.tracked && x.quantity > 0).productId",
             lambda j: [x for x in j["data"]["content"] if x["tracked"] and (x["quantity"] or 0) > 0][0]["productId"])],
     note="Lấy biến thể đầu tiên đang theo dõi tồn kho và còn hàng; lưu vào variantId / productId.")
step(F2, "Chi tiết sản phẩm", "GET", "/products/{{productId}}")
step(F2, "Sản phẩm liên quan", "GET", "/products/{{productId}}/related", query={"size": "8"})
step(F2, "Địa chỉ của user01", "GET", "/addresses/me", token="customerToken",
     saves=[("addressId", "j.data[0].id", lambda j: j["data"][0]["id"])])
step(F2, "Xu của tôi", "GET", "/coins/me", token="customerToken")
step(F2, "Báo giá đơn (phí ship, giảm giá)", "POST", "/orders/quote", token="customerToken",
     body={"items": [{"productVariantId": "{{variantId}}", "quantity": 1}]})
step(F2, "Đặt đơn COD", "POST", "/orders", token="customerToken",
     body={"addressId": "{{addressId}}", "note": "Đơn thử Postman {{runSuffix}}",
           "items": [{"productVariantId": "{{variantId}}", "quantity": 1}]},
     saves=[("orderId", "j.data.id", lambda j: j["data"]["id"])])
step(F2, "Đơn hàng của tôi", "GET", "/orders/{{orderId}}", token="customerToken")
step(F2, "Thêm vào yêu thích", "POST", "/wishlist/{{productId}}", token="customerToken")
step(F2, "Danh sách yêu thích", "GET", "/wishlist", token="customerToken")

step(F3, "Đếm đơn theo trạng thái", "GET", "/orders/counts", token="ownerToken")
step(F3, "Danh sách đơn", "GET", "/orders/page", token="ownerToken", query={"page": "0", "size": "20"})
step(F3, "Xác nhận đơn", "PUT", "/orders/{{orderId}}/status", token="ownerToken", query={"status": "DA_XAC_NHAN"})
step(F3, "Giao hàng (kèm mã vận đơn)", "PUT", "/orders/{{orderId}}/status", token="ownerToken",
     query={"status": "DANG_GIAO", "carrier": "GHN", "trackingCode": "GHN{{runSuffix}}"})
step(F3, "Ghi chú nội bộ", "PUT", "/orders/{{orderId}}/internal-note", token="ownerToken",
     body={"note": "Khách hẹn giao sau 17h"})
step(F3, "Khách xác nhận đã nhận hàng", "PUT", "/orders/{{orderId}}/status", token="customerToken",
     query={"status": "HOAN_THANH"})
step(F3, "Khách đánh giá sản phẩm", "POST", "/products/{{productId}}/reviews", token="customerToken",
     body={"rating": 5, "comment": "Hàng đẹp, giao nhanh"}, expect=None,
     note="Nếu user01 đã đánh giá sản phẩm này ở lần chạy trước, BE trả lỗi — bình thường.")
step(F3, "Đánh giá chưa trả lời", "GET", "/reviews/store", token="ownerToken", query={"unreplied": "true"})
step(F3, "Tổng hợp đánh giá sản phẩm", "GET", "/products/{{productId}}/reviews/summary")

step(F4, "Cửa hàng của tôi", "GET", "/stores/me", token="ownerToken")
step(F4, "Tạo danh mục cha", "POST", "/categories", token="ownerToken", body={"name": "Phụ kiện {{runSuffix}}"},
     saves=[("parentCategoryId", "j.data.id", lambda j: j["data"]["id"])])
step(F4, "Tạo danh mục con", "POST", "/categories", token="ownerToken",
     body={"name": "Tất", "parentId": "{{parentCategoryId}}"},
     saves=[("childCategoryId", "j.data.id", lambda j: j["data"]["id"])])
step(F4, "Xoá danh mục con", "DELETE", "/categories/{{childCategoryId}}", token="ownerToken")
step(F4, "Xoá danh mục cha", "DELETE", "/categories/{{parentCategoryId}}", token="ownerToken")
step(F4, "Sản phẩm của cửa hàng", "GET", "/products/mine", token="ownerToken")
step(F4, "Tổng quan kho", "GET", "/inventories/overview", token="ownerToken")
step(F4, "Hàng sắp hết", "GET", "/inventories/stock", token="ownerToken", query={"filter": "LOW", "size": "20"})
step(F4, "Lịch sử nhập / xuất của biến thể", "GET", "/inventories/variants/{{variantId}}/movements", token="ownerToken")
step(F4, "Nhà cung cấp", "GET", "/suppliers", token="ownerToken")
step(F4, "Phiếu nhập hàng", "GET", "/purchase-orders", token="ownerToken")
step(F4, "Khuyến mãi", "GET", "/promotions", token="ownerToken")

R = {"from": "{{fromDate}}", "to": "{{today}}"}
step(F5, "Dashboard", "GET", "/reports/dashboard", token="ownerToken")
step(F5, "Tổng kết doanh thu", "GET", "/reports/summary", token="ownerToken", query=R)
step(F5, "Doanh thu theo tháng", "GET", "/reports/revenue", token="ownerToken", query={**R, "groupBy": "MONTH"})
step(F5, "Sản phẩm bán chạy", "GET", "/reports/top-products", token="ownerToken", query=R)
step(F5, "Khách mới / quay lại", "GET", "/reports/customer-mix", token="ownerToken", query=R)
step(F5, "Hàng chậm bán", "GET", "/reports/slow-movers", token="ownerToken", query={"days": "30", "limit": "20"})
step(F5, "Gợi ý nhập hàng", "GET", "/reports/restock-suggestions", token="ownerToken",
     query={"days": "30", "horizon": "14", "cover": "30"})
step(F5, "Phân nhóm khách", "GET", "/customers/segments", token="ownerToken")

step(F6, "Danh sách cửa hàng đã duyệt", "GET", "/store-registrations", token="adminToken",
     query={"status": "APPROVED", "size": "20"})
step(F6, "Doanh thu toàn sàn theo cửa hàng", "GET", "/reports/stores", token="adminToken", query=R)
step(F6, "Báo cáo vi phạm chờ xử lý", "GET", "/violations", token="adminToken", query={"status": "PENDING"})


def build():
    data = bc.build()
    data["info"]["name"] = "Elsa Store API — dữ liệu mẫu (db.sql)"
    data["info"]["description"] = (
        "Chạy trên CSDL đã nạp db.sql. Trước khi chạy: mở tab Variables, điền ownerPassword (mật khẩu owner123); "
        "nếu BE không ở localhost:8080 thì sửa baseUrl. Chạy tuần tự bằng Collection Runner: thư mục 0 lấy token, "
        "các request sau tự dùng token / id đã lưu. Cả bộ sẽ đặt 1 đơn mới cho user01 và hoàn thành đơn đó.")
    keys = {v["key"] for v in data["variable"]}
    data["variable"] = [{"key": k, "value": DEFAULTS.get(k, "")} for k in DEFAULTS] + [
        v for v in data["variable"] if v["key"] not in DEFAULTS]
    assert keys | set(DEFAULTS) == {v["key"] for v in data["variable"]}
    return data


if __name__ == "__main__":
    out = sys.argv[1]
    with open(out, "w", encoding="utf-8") as f:
        json.dump(build(), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("wrote %s (%d requests)" % (out, len(bc.S)))
    if "--run" in sys.argv:
        i = sys.argv.index("--run")
        init = {k: v for k, v in DEFAULTS.items() if k != "baseUrl"}
        init.update(dict(a.split("=", 1) for a in sys.argv[i + 2:]))
        bc.run(sys.argv[i + 1], init)
