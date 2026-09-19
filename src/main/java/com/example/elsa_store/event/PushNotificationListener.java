package com.example.elsa_store.event;

import com.example.elsa_store.repository.DeviceTokenRepository;
import com.example.elsa_store.service.PushNotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/** Đẩy thông báo tới điện thoại ngoài luồng request, và chỉ khi giao dịch đã commit thật sự. */
@Component
@RequiredArgsConstructor
public class PushNotificationListener {

    private final PushNotificationSender pushNotificationSender;
    private final DeviceTokenRepository deviceTokenRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPushRequested(PushRequestedEvent event) {
        if (!pushNotificationSender.isEnabled() || event.userIds().isEmpty()) {
            return;
        }
        List<String> tokens = deviceTokenRepository.findTokensByUserIds(event.userIds()).stream()
                .distinct()
                .toList();
        if (tokens.isEmpty()) {
            return;
        }
        List<String> stale = pushNotificationSender.send(tokens, event.title(), event.body(), event.data());
        if (!stale.isEmpty()) {
            deviceTokenRepository.deleteAllByTokens(stale);
        }
    }
}
