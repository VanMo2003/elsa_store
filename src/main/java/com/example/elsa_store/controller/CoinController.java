package com.example.elsa_store.controller;

import com.example.elsa_store.dto.common.ApiResponse;
import com.example.elsa_store.dto.response.CoinWalletResponse;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.impl.CoinWallet;
import org.springframework.web.bind.annotation.*;

/** Xu thưởng của khách đang đăng nhập (tài khoản cửa hàng / ADMIN có số dư 0). */
@RestController
@RequestMapping("/coins")
public class CoinController {

    private final CoinWallet coinWallet;
    private final AccessGuard accessGuard;

    public CoinController(CoinWallet coinWallet, AccessGuard accessGuard) {
        this.coinWallet = coinWallet;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/me")
    public ApiResponse<CoinWalletResponse> mine(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(coinWallet.wallet(accessGuard.currentUser(), page, size));
    }
}
