package com.example.elsa_store.utils;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ký và kiểm chữ ký tham số VNPay (bản 2.1.0): chuỗi băm là các cặp {@code key=URLEncode(value)} sắp theo tên khoá,
 * nối bằng {@code &}, bỏ giá trị rỗng và hai trường chữ ký — đúng như mã mẫu của VNPay.
 */
public final class VNPayUtil {

    public static final String SECURE_HASH = "vnp_SecureHash";
    public static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

    private VNPayUtil() {}

    public static String hmacSHA512(String key, String data) {
        if (key == null || data == null) {
            throw new IllegalArgumentException("VNPay key and data are required");
        }
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            hmac512.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot compute the VNPay signature", e);
        }
    }

    /** Chuỗi đem đi băm (không gồm chữ ký). */
    public static String hashData(Map<String, String> params) {
        return query(params, false);
    }

    /** Query string gửi sang VNPay (khoá cũng được mã hoá URL). */
    public static String query(Map<String, String> params, boolean encodeKey) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .filter(entry -> !SECURE_HASH.equals(entry.getKey()) && !SECURE_HASH_TYPE.equals(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> (encodeKey ? URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII) : entry.getKey())
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }

    /** Chữ ký của tập tham số. */
    public static String sign(Map<String, String> params, String secretKey) {
        return hmacSHA512(secretKey, hashData(params));
    }

    /**
     * Tham số VNPay gửi về (return URL / IPN) có đúng do VNPay ký bằng khoá bí mật của cửa hàng không. So sánh thời gian
     * không đổi để không dò được chữ ký từng ký tự.
     */
    public static boolean verify(Map<String, String> params, String secretKey) {
        String received = params.get(SECURE_HASH);
        if (received == null || received.isBlank()) {
            return false;
        }
        String expected = sign(params, secretKey);
        return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    /** IP của người mua gửi kèm cho VNPay (chỉ để tham khảo; không dùng cho phân quyền). */
    public static String getIpAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        if (ip == null || ip.isBlank()) {
            return "127.0.0.1";
        }
        // VNPay giới hạn 45 ký tự (đủ cho IPv6).
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }
}
