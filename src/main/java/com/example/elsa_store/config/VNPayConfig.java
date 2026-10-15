package com.example.elsa_store.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Configuration
public class VNPayConfig {

    /** VNPay đọc ngày tạo / hết hạn theo giờ Việt Nam, bất kể máy chủ đặt múi giờ nào. */
    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Link thanh toán hết hạn sau từng này phút. */
    public static final int EXPIRE_MINUTES = 15;

    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${vnpay.url}")
    String vnp_PayUrl;

    @Value("${vnpay.returnUrl}")
    String vnp_ReturnUrl;

    @Value("${vnpay.tmnCode}")
    String vnp_TmnCode;

    @Value("${vnpay.secretKey}")
    String secretKey;

    @Value("${vnpay.version}")
    String vnp_Version;

    @Value("${vnpay.command}")
    String vnp_Command;

    @Value("${vnpay.orderType}")
    String orderType;

    /** Bộ tham số cố định của một lần thanh toán; người gọi thêm số tiền, mã giao dịch, nội dung, IP. */
    public Map<String, String> getVNPayConfig() {
        Map<String, String> vnpParamsMap = new HashMap<>();
        vnpParamsMap.put("vnp_Version", this.vnp_Version);
        vnpParamsMap.put("vnp_Command", this.vnp_Command);
        vnpParamsMap.put("vnp_TmnCode", this.vnp_TmnCode);
        vnpParamsMap.put("vnp_CurrCode", "VND");
        vnpParamsMap.put("vnp_OrderType", this.orderType);
        vnpParamsMap.put("vnp_Locale", "vn");
        vnpParamsMap.put("vnp_ReturnUrl", this.vnp_ReturnUrl);
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        vnpParamsMap.put("vnp_CreateDate", VNP_DATE.format(now));
        vnpParamsMap.put("vnp_ExpireDate", VNP_DATE.format(now.plusMinutes(EXPIRE_MINUTES)));
        return vnpParamsMap;
    }
}
