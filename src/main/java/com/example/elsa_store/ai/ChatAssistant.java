package com.example.elsa_store.ai;

import com.example.elsa_store.constant.ChatSide;
import com.example.elsa_store.dto.request.AiStoreSettingsRequest;
import com.example.elsa_store.dto.response.AiStoreSettingsResponse;
import com.example.elsa_store.dto.response.AiSuggestionResponse;
import com.example.elsa_store.dto.response.ChatProductResponse;
import com.example.elsa_store.entity.ChatMessage;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.ChatMessageRepository;
import com.example.elsa_store.repository.ConversationRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.ChatService;
import com.example.elsa_store.service.impl.ChatProducts;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Trợ lý AI của cửa hàng trong khung chat với khách. Ba cách dùng:
 * <ul>
 *   <li><b>Khách hỏi AI</b> — khách gửi tin kèm {@code askAi}, trợ lý trả lời ngay ({@link #askNow}); gợi ý sản phẩm
 *       phù hợp câu hỏi dưới dạng thẻ sản phẩm.
 *   <li><b>Gợi ý trả lời</b> — nhân viên xin bản nháp ({@link #suggest}); không gửi gì cho tới khi họ tự gửi.
 *   <li><b>Tự trả lời</b> — cửa hàng bật tuỳ chọn thì tin của khách mà sau {@code ai.auto-reply-delay-seconds} giây
 *       chưa ai trả lời sẽ được trợ lý trả lời với nhãn "Trợ lý AI" ({@link #onCustomerMessage}).
 * </ul>
 * Mọi lần trả lời đều đi qua {@link ProductAssistant} (có căn cứ, không bịa giá/tồn kho).
 */
@Slf4j
@Service
public class ChatAssistant {

    private static final int HISTORY_SIZE = 20;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    static final String BUSY_REPLY =
            "Trợ lý AI đang bận nên chưa trả lời được. Cửa hàng sẽ phản hồi bạn sớm nhé.";
    static final String RATE_LIMITED_REPLY =
            "Bạn hỏi trợ lý hơi nhiều trong thời gian ngắn. Vui lòng thử lại sau ít phút hoặc chờ cửa hàng phản hồi.";

    private final AiClient ai;
    private final ProductAssistant assistant;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StoreRepository storeRepository;
    private final ChatService chatService;
    private final ChatProducts chatProducts;
    private final AccessGuard accessGuard;
    private final AiRateLimiter rateLimiter;
    private final long autoReplyDelaySeconds;

    // Độ trễ chạy trên một luồng nhỏ; lời gọi mô hình (vài giây mỗi lần) chạy trên luồng ảo.
    private final ScheduledExecutorService delays = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public ChatAssistant(
            AiClient ai,
            ProductAssistant assistant,
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            StoreRepository storeRepository,
            ChatService chatService,
            ChatProducts chatProducts,
            AccessGuard accessGuard,
            AiRateLimiter rateLimiter,
            @Value("${ai.auto-reply-delay-seconds:60}") long autoReplyDelaySeconds) {
        this.ai = ai;
        this.assistant = assistant;
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.storeRepository = storeRepository;
        this.chatService = chatService;
        this.chatProducts = chatProducts;
        this.accessGuard = accessGuard;
        this.rateLimiter = rateLimiter;
        this.autoReplyDelaySeconds = autoReplyDelaySeconds;
    }

    @PreDestroy
    void shutdown() {
        delays.shutdownNow();
        workers.shutdownNow();
    }

    // ---- Gợi ý trả lời (nhân viên xin bản nháp) ----

    public AiSuggestionResponse suggest(Long conversationId) {
        var ref = conversationRepository
                .findRef(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found id=" + conversationId));
        var caller = accessGuard.authorize(ref.getStoreId(), Access.STAFF_WRITE);
        if (!ai.isEnabled()) {
            throw new AppException(ErrorCode.AI_UNAVAILABLE);
        }
        rateLimiter.check("suggest:" + caller.getId(), 30, Duration.ofMinutes(10));
        List<ProductAssistant.Line> history = history(conversationId);
        if (history.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "The customer hasn't written anything yet");
        }
        ProductAssistant.Reply reply =
                assistant.generate(ref.getStoreId(), ref.getCustomerId(), history, ProductAssistant.Mode.SUGGEST);
        return toSuggestion(reply);
    }

    // ---- Khách hỏi AI ----

    /** Khách vừa gửi tin kèm {@code askAi}: trả lời ngay trên luồng nền. */
    public void askNow(Long conversationId, Long messageId) {
        if (!ai.isEnabled()) {
            return;
        }
        workers.execute(() -> answer(conversationId, messageId, ProductAssistant.Mode.ASK));
    }

    // ---- Tự trả lời ----

    /** Gọi sau khi tin của khách đã commit; trả lời muộn hơn nếu cửa hàng bật tự trả lời và chưa ai trả lời. */
    public void onCustomerMessage(Long conversationId, Long messageId) {
        if (!ai.isEnabled()) {
            return;
        }
        delays.schedule(
                () -> workers.execute(() -> answer(conversationId, messageId, ProductAssistant.Mode.AUTO)),
                autoReplyDelaySeconds,
                TimeUnit.SECONDS);
    }

    /** Chạy trên luồng nền; lỗi chỉ ghi log, riêng khách chủ động hỏi thì nhận một tin báo bận. */
    private void answer(Long conversationId, Long messageId, ProductAssistant.Mode mode) {
        boolean explicit = mode == ProductAssistant.Mode.ASK;
        try {
            var ref = conversationRepository.findRef(conversationId).orElse(null);
            if (ref == null) {
                return;
            }
            if (mode == ProductAssistant.Mode.AUTO) {
                boolean enabled = storeRepository
                        .findById(ref.getStoreId())
                        .map(s -> Boolean.TRUE.equals(s.getAiAutoReply()))
                        .orElse(false);
                if (!enabled) {
                    return;
                }
            }
            List<ProductAssistant.Line> history = history(conversationId);
            // Tự trả lời: đã có người trả lời, hoặc khách còn gõ tiếp — tin mới nhất tự đặt lượt của nó.
            if (mode == ProductAssistant.Mode.AUTO && (history.isEmpty() || !latestIs(conversationId, messageId))) {
                return;
            }
            try {
                if (explicit) {
                    rateLimiter.check("ask-user:" + ref.getCustomerId(), 15, Duration.ofMinutes(10));
                }
                rateLimiter.check((explicit ? "ask:" : "auto:") + conversationId, explicit ? 40 : 20, Duration.ofHours(1));
            } catch (AppException limited) {
                if (explicit) {
                    chatService.postAssistantReply(
                            conversationId, messageId, RATE_LIMITED_REPLY, List.of(), false, null, true);
                }
                return;
            }
            ProductAssistant.Reply reply;
            try {
                reply = assistant.generate(ref.getStoreId(), ref.getCustomerId(), history, mode);
            } catch (AppException failed) {
                log.warn("Chat assistant could not answer conversation {}: {}", conversationId, failed.getMessage());
                if (explicit) {
                    // Khách đang chờ: báo bận và nhờ nhân viên tiếp.
                    chatService.postAssistantReply(
                            conversationId, messageId, BUSY_REPLY, List.of(), true, "Trợ lý AI không trả lời được", true);
                }
                return;
            }
            chatService.postAssistantReply(
                    conversationId,
                    messageId,
                    reply.content(),
                    reply.productIds(),
                    reply.handoff(),
                    reply.handoffReason(),
                    explicit);
        } catch (RuntimeException e) {
            log.warn("Chat assistant could not answer conversation {}: {}", conversationId, e.getMessage());
        }
    }

    private boolean latestIs(Long conversationId, Long messageId) {
        List<ChatMessage> latest =
                chatMessageRepository.findByConversation_IdOrderByIdDesc(conversationId, PageRequest.of(0, 1));
        return !latest.isEmpty() && latest.getFirst().getId().equals(messageId);
    }

    // ---- Cài đặt của cửa hàng ----

    public AiStoreSettingsResponse settings() {
        return settingsOf(accessGuard.requireStoreMember());
    }

    /** Chủ hoặc quản lý bật / tắt tự trả lời. */
    public AiStoreSettingsResponse updateSettings(AiStoreSettingsRequest req) {
        Store store = accessGuard.requireStoreManager();
        store.setAiAutoReply(req.getAutoReply());
        return settingsOf(storeRepository.save(store));
    }

    private AiStoreSettingsResponse settingsOf(Store store) {
        return AiStoreSettingsResponse.builder()
                .storeId(store.getId())
                .autoReply(Boolean.TRUE.equals(store.getAiAutoReply()))
                .aiEnabled(ai.isEnabled())
                .autoReplyDelaySeconds(autoReplyDelaySeconds)
                .build();
    }

    // ---- Chuyển đổi ----

    AiSuggestionResponse toSuggestion(ProductAssistant.Reply reply) {
        Map<Long, ChatProductResponse> products = chatProducts.load(reply.productIds());
        return AiSuggestionResponse.builder()
                .content(reply.content())
                .products(products.isEmpty() ? null : new ArrayList<>(products.values()))
                .needsStaff(reply.handoff())
                .handoffReason(reply.handoffReason())
                .toolsUsed(reply.toolsUsed())
                .build();
    }

    /** Hai mươi tin gần nhất, cũ trước; tin đính kèm sản phẩm ghi kèm tên để trợ lý biết khách đang nói về gì. */
    private List<ProductAssistant.Line> history(Long conversationId) {
        List<ChatMessage> newestFirst = new ArrayList<>(chatMessageRepository.findByConversation_IdOrderByIdDesc(
                conversationId, PageRequest.of(0, HISTORY_SIZE)));
        Collections.reverse(newestFirst);
        Set<Long> ids = new LinkedHashSet<>();
        newestFirst.forEach(m -> ids.addAll(m.productIdList()));
        Map<Long, ChatProductResponse> products = chatProducts.load(ids);

        List<ProductAssistant.Line> lines = new ArrayList<>();
        for (ChatMessage message : newestFirst) {
            String who;
            if (message.getSenderSide() == ChatSide.CUSTOMER) {
                who = "Khách";
            } else if (Boolean.TRUE.equals(message.getAiGenerated())) {
                who = "Cửa hàng (trợ lý AI)";
            } else {
                who = "Cửa hàng (" + message.getSenderName() + ")";
            }
            String stamp = message.getCreatedAt() == null ? "" : message.getCreatedAt().format(STAMP);
            lines.add(new ProductAssistant.Line(who, stamp, withAttachments(message, products)));
        }
        return lines;
    }

    static String withAttachments(ChatMessage message, Map<Long, ChatProductResponse> products) {
        List<String> names = message.productIdList().stream()
                .map(products::get)
                .filter(java.util.Objects::nonNull)
                .map(p -> p.getName() + " (mã " + p.getId() + ")")
                .toList();
        return names.isEmpty()
                ? message.getContent()
                : message.getContent() + " [đính kèm sản phẩm: " + String.join(", ", names) + "]";
    }
}
