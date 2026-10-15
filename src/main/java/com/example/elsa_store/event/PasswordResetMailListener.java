package com.example.elsa_store.event;

import com.example.elsa_store.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Gửi mã khôi phục ngoài luồng request (SMTP chậm không làm chậm API) và chỉ khi mã đã được lưu thật. */
@Component
@RequiredArgsConstructor
public class PasswordResetMailListener {

    private final MailService mailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onResetRequested(PasswordResetRequestedEvent event) {
        mailService.sendPasswordReset(event);
    }
}
