package com.example.elsa_store.event;

import com.example.elsa_store.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Gửi email đơn hàng ngoài luồng request, và chỉ khi giao dịch đã commit thật sự. */
@Component
@RequiredArgsConstructor
public class OrderMailListener {

    private final MailService mailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderMail(OrderMailEvent event) {
        mailService.sendOrderMail(event);
    }
}
