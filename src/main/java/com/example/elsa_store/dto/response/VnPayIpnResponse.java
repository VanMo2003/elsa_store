package com.example.elsa_store.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Câu trả lời cho IPN theo đúng định dạng VNPay yêu cầu: {@code {"RspCode":"00","Message":"Confirm Success"}}. VNPay
 * gửi lại IPN tới khi nhận RspCode 00 hoặc 02 (đã xác nhận trước đó).
 */
public record VnPayIpnResponse(
        @JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {

    public static final VnPayIpnResponse CONFIRMED = new VnPayIpnResponse("00", "Confirm Success");
    public static final VnPayIpnResponse ALREADY_CONFIRMED = new VnPayIpnResponse("02", "Order already confirmed");
    public static final VnPayIpnResponse ORDER_NOT_FOUND = new VnPayIpnResponse("01", "Order not found");
    public static final VnPayIpnResponse INVALID_AMOUNT = new VnPayIpnResponse("04", "Invalid amount");
    public static final VnPayIpnResponse INVALID_SIGNATURE = new VnPayIpnResponse("97", "Invalid signature");
    public static final VnPayIpnResponse UNKNOWN_ERROR = new VnPayIpnResponse("99", "Unknown error");
}
