
package com.example.elsa_store.service;

import com.example.elsa_store.dto.request.PaymentRequest;
import com.example.elsa_store.dto.response.PaymentResponse;
import com.example.elsa_store.dto.response.PaymentVnPayResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface PaymentService {
    PaymentResponse create(PaymentRequest req);
    PaymentResponse update(Long id, PaymentRequest req);
    void delete(Long id);
    PaymentResponse getById(Long id);
    List<PaymentResponse> getAll();
    PaymentVnPayResponse createVnPayPayment(HttpServletRequest request);
    void handleVnPayCallback(HttpServletRequest request);
}
