package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.ChatSide;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.ChatMessageRequest;
import com.example.elsa_store.dto.request.ConversationCreateRequest;
import com.example.elsa_store.dto.response.ChatMessageResponse;
import com.example.elsa_store.dto.response.ChatProductResponse;
import com.example.elsa_store.dto.response.ChatUnreadResponse;
import com.example.elsa_store.dto.response.ConversationResponse;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.entity.ChatMessage;
import com.example.elsa_store.entity.Conversation;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.event.ChatUpdatedEvent;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.mapper.ChatMapper;
import com.example.elsa_store.repository.ChatMessageRepository;
import com.example.elsa_store.repository.ConversationRepository;
import com.example.elsa_store.repository.StoreRepository;
import com.example.elsa_store.repository.UserRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.service.FileStorageService;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.ChatService;
import com.example.elsa_store.utils.NotificationMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chat khách ↔ cửa hàng. Mỗi cuộc trò chuyện thuộc một khách và một cửa hàng; ở phía cửa hàng, chủ, quản lý và mọi
 * nhân viên dùng chung. ADMIN không có quyền truy cập. Mọi thay đổi phát một {@link ChatUpdatedEvent}, được
 * {@code ChatEventListener} đẩy qua WebSocket sau khi commit (và push cho người bên kia nếu họ đang offline).
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    public static final String ASSISTANT_NAME = "Trợ lý AI";
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort INBOX_ORDER =
            Sort.by(Sort.Direction.DESC, "lastMessageAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ChatProducts chatProducts;
    private final AccessGuard accessGuard;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transaction;
    private final FileStorageService fileStorageService;

    @Override
    public ConversationResponse open(ConversationCreateRequest req) {
        User customer = requireCustomer();
        Store store = storeRepository
                .findById(req.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id=" + req.getStoreId()));
        if (!store.isApproved()) {
            throw new AppException(ErrorCode.STORE_NOT_APPROVED);
        }
        try {
            return transaction.execute(status -> openOrCreate(customer, store));
        } catch (DataIntegrityViolationException e) {
            // Hai lần mở đồng thời (bấm đúp) cùng chèn một cặp khách - cửa hàng: lần này đọc lại cuộc vừa được tạo.
            return transaction.execute(status -> openOrCreate(customer, store));
        }
    }

    private ConversationResponse openOrCreate(User customer, Store store) {
        Conversation conversation = conversationRepository
                .findByCustomer_IdAndStore_Id(customer.getId(), store.getId())
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder().customer(customer).store(store).build()));
        return ChatMapper.forSide(conversation, ChatSide.CUSTOMER);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> getMine(int page, int size) {
        User customer = requireCustomer();
        Page<Conversation> conversations = conversationRepository.findAllByCustomer_IdAndLastMessageAtIsNotNull(
                customer.getId(), inboxPage(page, size));
        return PageResponse.from(conversations.map(c -> ChatMapper.forSide(c, ChatSide.CUSTOMER)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> getInbox(int page, int size) {
        Store store = accessGuard.requireStoreMember();
        Page<Conversation> conversations =
                conversationRepository.findAllByStore_IdAndLastMessageAtIsNotNull(store.getId(), inboxPage(page, size));
        return PageResponse.from(conversations.map(c -> ChatMapper.forSide(c, ChatSide.STORE)));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getById(Long id) {
        Conversation conversation = find(id);
        return ChatMapper.forSide(conversation, sideOf(accessGuard.currentUser(), conversation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long conversationId, Long beforeId, int size) {
        Conversation conversation = find(conversationId);
        sideOf(accessGuard.currentUser(), conversation);
        Pageable limit = PageRequest.of(0, Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        List<ChatMessage> messages = beforeId == null
                ? chatMessageRepository.findByConversation_IdOrderByIdDesc(conversationId, limit)
                : chatMessageRepository.findByConversation_IdAndIdLessThanOrderByIdDesc(
                        conversationId, beforeId, limit);
        return toResponses(messages);
    }

    @Override
    @Transactional
    public ChatMessageResponse send(Long conversationId, ChatMessageRequest req) {
        User caller = accessGuard.currentUser();
        Conversation conversation = findForUpdate(conversationId);
        ChatSide side = sideOf(caller, conversation);
        String content = req.getContent().strip();
        if (content.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "content: must not be blank");
        }
        List<Long> productIds;
        try {
            productIds = chatProducts.requireAttachable(
                    req.getProductIds(), conversation.getStore().getId());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, e.getMessage());
        }

        boolean askAssistant = side == ChatSide.CUSTOMER && Boolean.TRUE.equals(req.getAskAi());
        return post(conversation, caller, side, ChatMessage.builder()
                .content(content)
                .productIds(ChatMessage.joinProductIds(productIds)), content, askAssistant);
    }

    @Override
    @Transactional
    public ChatMessageResponse sendImages(Long conversationId, List<MultipartFile> files, String caption) {
        User caller = accessGuard.currentUser();
        Conversation conversation = findForUpdate(conversationId);
        ChatSide side = sideOf(caller, conversation);
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "files: must not be empty");
        }
        if (files.size() > ChatMessage.MAX_IMAGES) {
            throw new AppException(ErrorCode.BAD_REQUEST, "A message can have at most " + ChatMessage.MAX_IMAGES + " images");
        }
        String content = caption == null ? "" : caption.strip();
        if (content.length() > 2000) {
            throw new AppException(ErrorCode.BAD_REQUEST, "content: size must be at most 2000");
        }
        List<String> urls = files.stream().map(fileStorageService::storeChatImage).toList();
        // Tin chỉ có ảnh: danh sách trò chuyện và thông báo hiện "[Ảnh]".
        String preview = content.isEmpty() ? "[Ảnh]" : content;
        return post(conversation, caller, side, ChatMessage.builder()
                .content(content)
                .imageUrls(ChatMessage.joinImages(urls)), preview, false);
    }

    /** Lưu tin của {@code caller}, cập nhật số chưa đọc của hai phía và phát sự kiện realtime / push. */
    private ChatMessageResponse post(Conversation conversation, User caller, ChatSide side,
                                     ChatMessage.ChatMessageBuilder draft, String preview, boolean askAssistant) {
        ChatMessage message = chatMessageRepository.save(draft
                .conversation(conversation)
                .senderSide(side)
                .senderUserId(caller.getId())
                .senderName(ChatMapper.displayName(caller))
                .senderRole(caller.getRole())
                .build());

        touch(conversation, side, message, preview);
        // Viết tin nghĩa là đã đọc cuộc trò chuyện; phía bên kia có thêm một tin chưa đọc.
        if (side == ChatSide.CUSTOMER) {
            conversation.setCustomerUnread(0);
            conversation.setStoreUnread(conversation.getStoreUnread() + 1);
        } else {
            conversation.setStoreUnread(0);
            conversation.setCustomerUnread(conversation.getCustomerUnread() + 1);
            // Một người thật của cửa hàng đã tiếp quản từ trợ lý.
            conversation.setNeedsStaff(null);
            conversation.setHandoffReason(null);
        }
        conversationRepository.save(conversation);

        ChatMessageResponse response = toResponse(message);
        eventPublisher.publishEvent(
                event(ChatUpdatedEvent.Type.MESSAGE, conversation, response, caller.getId(), askAssistant));
        return response;
    }

    @Override
    @Transactional
    public ChatMessageResponse postAssistantReply(
            Long conversationId,
            Long replyToMessageId,
            String content,
            List<Long> productIds,
            boolean handoff,
            String handoffReason,
            boolean explicitAsk) {
        Conversation conversation = findForUpdate(conversationId);
        if (!shouldStillAnswer(conversationId, replyToMessageId, explicitAsk)) {
            return null;
        }
        String text = content.strip();
        if (text.length() > 2000) {
            text = text.substring(0, 2000);
        }
        List<Long> attach = productIds == null
                ? List.of()
                : productIds.stream().distinct().limit(ChatProducts.MAX_PER_MESSAGE).toList();
        ChatMessage message = chatMessageRepository.save(ChatMessage.builder()
                .conversation(conversation)
                .senderSide(ChatSide.STORE)
                .senderName(ASSISTANT_NAME)
                .aiGenerated(true)
                .content(text)
                .productIds(ChatMessage.joinProductIds(attach))
                .build());

        touch(conversation, ChatSide.STORE, message, text);
        // Khách có câu trả lời mới; đội ngũ vẫn còn tin của khách để xem.
        conversation.setCustomerUnread(conversation.getCustomerUnread() + 1);
        if (handoff) {
            conversation.setNeedsStaff(true);
            conversation.setHandoffReason(handoffReason == null ? null : truncate(handoffReason.strip(), 255));
        }
        conversationRepository.save(conversation);

        ChatMessageResponse response = toResponse(message);
        eventPublisher.publishEvent(event(ChatUpdatedEvent.Type.MESSAGE, conversation, response, null, false));
        return response;
    }

    /**
     * Có người trả lời (hoặc khách viết tiếp) trong lúc mô hình đang nghĩ thì lượt mới hơn thắng: tự trả lời chỉ
     * khi tin được trả lời vẫn là tin cuối; khách chủ động hỏi AI thì chỉ nhường khi nhân viên thật đã trả lời sau đó.
     */
    private boolean shouldStillAnswer(Long conversationId, Long replyToMessageId, boolean explicitAsk) {
        if (explicitAsk) {
            return !chatMessageRepository.existsByConversation_IdAndIdGreaterThanAndSenderSideAndAiGeneratedIsNull(
                    conversationId, replyToMessageId, ChatSide.STORE);
        }
        List<ChatMessage> latest =
                chatMessageRepository.findByConversation_IdOrderByIdDesc(conversationId, PageRequest.of(0, 1));
        return !latest.isEmpty() && latest.getFirst().getId().equals(replyToMessageId);
    }

    @Override
    @Transactional
    public ConversationResponse markRead(Long conversationId) {
        User caller = accessGuard.currentUser();
        Conversation conversation = findForUpdate(conversationId);
        ChatSide side = sideOf(caller, conversation);
        boolean changed =
                side == ChatSide.CUSTOMER ? conversation.getCustomerUnread() > 0 : conversation.getStoreUnread() > 0;
        if (changed) {
            if (side == ChatSide.CUSTOMER) {
                conversation.setCustomerUnread(0);
            } else {
                conversation.setStoreUnread(0);
            }
            conversationRepository.save(conversation);
            eventPublisher.publishEvent(
                    event(ChatUpdatedEvent.Type.READ, conversation, null, caller.getId(), false));
        }
        return ChatMapper.forSide(conversation, side);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatUnreadResponse countUnread() {
        User caller = accessGuard.currentUser();
        long unread;
        if (caller.getRole() == Role.USER) {
            unread = conversationRepository.sumCustomerUnread(caller.getId());
        } else {
            Store store = accessGuard.requireStoreMember();
            unread = conversationRepository.sumStoreUnread(store.getId());
        }
        return ChatUnreadResponse.builder().unread(unread).build();
    }

    // ------------------------------------------------------------------ nội bộ

    private void touch(Conversation conversation, ChatSide side, ChatMessage message, String content) {
        conversation.setLastMessage(NotificationMessages.preview(content));
        conversation.setLastSenderSide(side);
        conversation.setLastMessageAt(message.getCreatedAt() == null ? LocalDateTime.now() : message.getCreatedAt());
    }

    private User requireCustomer() {
        User caller = accessGuard.currentUser();
        if (caller.getRole() != Role.USER) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        return caller;
    }

    /** Người gọi nói thay cho phía nào của cuộc trò chuyện; người khác (kể cả ADMIN) bị từ chối. */
    private ChatSide sideOf(User caller, Conversation conversation) {
        if (caller.getRole() == Role.USER) {
            if (!conversation.getCustomer().getId().equals(caller.getId())) {
                throw new AppException(ErrorCode.UN_AUTHORIZED);
            }
            return ChatSide.CUSTOMER;
        }
        if (caller.getRole() == Role.ADMIN) {
            throw new AppException(ErrorCode.UN_AUTHORIZED);
        }
        accessGuard.authorize(conversation.getStore(), Access.STAFF_WRITE);
        return ChatSide.STORE;
    }

    /** Chủ, quản lý, nhân viên của cửa hàng — mọi người trả lời chat của cửa hàng. */
    List<Long> teamUserIds(Store store) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(store.getOwner().getId());
        userRepository.findAllByStore_Id(store.getId()).stream()
                .filter(User::isEnabled)
                .forEach(u -> ids.add(u.getId()));
        return new ArrayList<>(ids);
    }

    private ChatUpdatedEvent event(
            ChatUpdatedEvent.Type type,
            Conversation conversation,
            ChatMessageResponse message,
            Long senderUserId,
            boolean askAssistant) {
        return new ChatUpdatedEvent(
                type,
                message,
                ChatMapper.forSide(conversation, ChatSide.CUSTOMER),
                ChatMapper.forSide(conversation, ChatSide.STORE),
                conversation.getCustomer().getId(),
                teamUserIds(conversation.getStore()),
                senderUserId,
                askAssistant);
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return toResponses(List.of(message)).getFirst();
    }

    /** Gom mã sản phẩm của cả trang tin để tải một lần, tránh truy vấn từng tin. */
    private List<ChatMessageResponse> toResponses(List<ChatMessage> messages) {
        Set<Long> ids = new LinkedHashSet<>();
        messages.forEach(m -> ids.addAll(m.productIdList()));
        Map<Long, ChatProductResponse> products = chatProducts.load(ids);
        return messages.stream().map(m -> ChatMapper.toMessageResponse(m, products)).toList();
    }

    private Conversation find(Long id) {
        return conversationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found id=" + id));
    }

    /** Cho các thao tác ghi: giữ khoá hàng tới hết giao dịch để các lần ghi trên cùng cuộc trò chuyện nối đuôi nhau. */
    private Conversation findForUpdate(Long id) {
        return conversationRepository
                .findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found id=" + id));
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static Pageable inboxPage(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), INBOX_ORDER);
    }
}
