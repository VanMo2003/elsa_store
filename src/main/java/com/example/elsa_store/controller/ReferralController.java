package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.request.ReferralApplyRequest;
import com.example.elsa_store.dto.response.ReferralResponse;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.impl.Referrals;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Giới thiệu bạn bè (chỉ khách). */
@RestController
@RequestMapping("/referrals")
public class ReferralController {

    private final Referrals referrals;
    private final AccessGuard accessGuard;

    public ReferralController(Referrals referrals, AccessGuard accessGuard) {
        this.referrals = referrals;
        this.accessGuard = accessGuard;
    }

    /** Mã giới thiệu của mình (sinh lần đầu), số bạn đã mời / đã thưởng, xu đã nhận, quy tắc thưởng. */
    @GetMapping("/me")
    public ApiResponse<ReferralResponse> mine() {
        return ApiResponse.ok(referrals.mine(accessGuard.currentUser()));
    }

    /** Nhập mã giới thiệu của người khác (trong 7 ngày đầu, khi chưa có đơn hoàn thành). */
    @PostMapping("/apply")
    public ApiResponse<ReferralResponse> apply(@Valid @RequestBody ReferralApplyRequest request) {
        return ApiResponse.ok(referrals.apply(accessGuard.currentUser(), request.code()));
    }
}
