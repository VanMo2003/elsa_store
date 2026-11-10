package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.CheckInResponse;
import com.example.elsa_store.dto.response.CoinWalletResponse;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.impl.CoinWallet;
import com.example.elsa_store.service.impl.DailyCheckIns;
import org.springframework.web.bind.annotation.*;

/** Xu thưởng của khách đang đăng nhập (tài khoản cửa hàng / ADMIN có số dư 0). */
@RestController
@RequestMapping("/coins")
public class CoinController {

    private final CoinWallet coinWallet;
    private final AccessGuard accessGuard;
    private final DailyCheckIns checkIns;

    public CoinController(CoinWallet coinWallet, AccessGuard accessGuard, DailyCheckIns checkIns) {
        this.coinWallet = coinWallet;
        this.accessGuard = accessGuard;
        this.checkIns = checkIns;
    }

    /** Trạng thái điểm danh nhận xu (chỉ khách). */
    @GetMapping("/check-in")
    public ApiResponse<CheckInResponse> checkInStatus() {
        return ApiResponse.ok(checkIns.status(accessGuard.currentUser()));
    }

    /** Điểm danh hôm nay; đã điểm danh rồi thì báo 1030. */
    @PostMapping("/check-in")
    public ApiResponse<CheckInResponse> checkIn() {
        return ApiResponse.ok(checkIns.checkIn(accessGuard.currentUser()));
    }

    @GetMapping("/me")
    public ApiResponse<CoinWalletResponse> mine(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(coinWallet.wallet(accessGuard.currentUser(), page, size));
    }
}
