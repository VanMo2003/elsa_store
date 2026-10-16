package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.NotificationType;
import com.example.elsa_store.constant.Role;
import com.example.elsa_store.dto.request.AnswerRequest;
import com.example.elsa_store.dto.request.QuestionRequest;
import com.example.elsa_store.dto.response.PageResponse;
import com.example.elsa_store.dto.response.QuestionResponse;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductQuestion;
import com.example.elsa_store.entity.Store;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.exception.AppException;
import com.example.elsa_store.exception.ErrorCode;
import com.example.elsa_store.exception.ResourceNotFoundException;
import com.example.elsa_store.repository.ProductQuestionRepository;
import com.example.elsa_store.repository.ProductRepository;
import com.example.elsa_store.security.AccessGuard;
import com.example.elsa_store.security.AccessGuard.Access;
import com.example.elsa_store.service.NotificationService;
import com.example.elsa_store.service.ProductQuestionService;
import com.example.elsa_store.utils.AppLinks;
import com.example.elsa_store.utils.NotificationMessages;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class ProductQuestionServiceImpl implements ProductQuestionService {

    private final ProductQuestionRepository questionRepository;
    private final ProductRepository productRepository;
    private final AccessGuard accessGuard;
    private final NotificationService notificationService;

    public ProductQuestionServiceImpl(ProductQuestionRepository questionRepository,
                                      ProductRepository productRepository,
                                      AccessGuard accessGuard,
                                      NotificationService notificationService) {
        this.questionRepository = questionRepository;
        this.productRepository = productRepository;
        this.accessGuard = accessGuard;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> list(Long productId, int page, int size) {
        visibleProduct(productId);
        Long viewerId = accessGuard.currentUserOrEmpty().map(User::getId).orElse(null);
        var result = questionRepository.findForProduct(productId, viewerId, pageOf(page, size));
        return PageResponse.from(result.map(q -> toResponse(q, viewerId)));
    }

    @Override
    public QuestionResponse ask(Long productId, QuestionRequest request) {
        User user = accessGuard.currentUser();
        if (user.getRole() != Role.USER) {
            throw new AppException(ErrorCode.UN_AUTHORIZED, "Only customers can ask questions");
        }
        Product product = visibleProduct(productId);
        String text = trimToNull(request.getQuestion());
        if (text == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "question: must not be blank");
        }
        if (questionRepository.countByUser_IdAndProduct_IdAndAnswerIsNull(user.getId(), productId)
                >= ProductQuestion.MAX_PENDING_PER_PRODUCT) {
            throw new AppException(ErrorCode.TOO_MANY_PENDING_QUESTIONS);
        }
        ProductQuestion saved = questionRepository.save(ProductQuestion.builder()
                .product(product)
                .user(user)
                .question(text)
                .build());
        if (product.getStore() != null) {
            notificationService.notifyStore(product.getStore(), NotificationType.QUESTION_NEW,
                    NotificationMessages.questionNew(saved), AppLinks.storeQuestions(), saved.getId());
        }
        return toResponse(saved, user.getId());
    }

    @Override
    public void delete(Long questionId) {
        ProductQuestion question = find(questionId);
        User current = accessGuard.currentUser();
        boolean asker = question.getUser().getId().equals(current.getId());
        if (!asker && current.getRole() != Role.ADMIN) {
            accessGuard.authorize(question.getProduct().getStore(), Access.STAFF_WRITE);
        }
        questionRepository.delete(question);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestionResponse> storeQuestions(boolean unanswered, int page, int size) {
        Store store = accessGuard.requireStoreMember();
        var result = questionRepository.findForStore(store.getId(), unanswered, pageOf(page, size));
        return PageResponse.from(result.map(q -> toResponse(q, null)));
    }

    @Override
    public QuestionResponse answer(Long questionId, AnswerRequest request) {
        ProductQuestion question = find(questionId);
        User staff = accessGuard.authorize(question.getProduct().getStore(), Access.STAFF_WRITE);
        String answer = trimToNull(request.getAnswer());
        if (answer == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "answer: must not be blank");
        }
        boolean changed = !answer.equals(question.getAnswer());
        question.setAnswer(answer);
        question.setAnsweredAt(LocalDateTime.now());
        question.setAnsweredBy(staff);
        ProductQuestion saved = questionRepository.save(question);
        if (changed) {
            notificationService.notify(saved.getUser(), NotificationType.QUESTION_ANSWERED,
                    NotificationMessages.questionAnswered(saved), AppLinks.product(saved.getProduct().getId()),
                    saved.getProduct().getId());
        }
        return toResponse(saved, null);
    }

    // ------------------------------------------------------------------ nội bộ

    /** Sản phẩm đang bán, hoặc người của chính cửa hàng / ADMIN xem cả sản phẩm đang ẩn — giống trang chi tiết. */
    private Product visibleProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (ProductServiceImpl.isPublic(product)) {
            return product;
        }
        User current = accessGuard.currentUserOrEmpty().orElse(null);
        if (current != null) {
            if (current.getRole() == Role.ADMIN) {
                return product;
            }
            Store mine = accessGuard.storeOf(current);
            if (mine != null && product.getStore() != null && mine.getId().equals(product.getStore().getId())) {
                return product;
            }
        }
        throw new ResourceNotFoundException("Product not found");
    }

    private ProductQuestion find(Long id) {
        return questionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Question not found"));
    }

    private static PageRequest pageOf(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static QuestionResponse toResponse(ProductQuestion q, Long viewerId) {
        Product p = q.getProduct();
        return QuestionResponse.builder()
                .id(q.getId())
                .productId(p.getId())
                .productName(p.getName())
                .productImageUrl(p.getImageUrl())
                .question(q.getQuestion())
                .askerName(ProductReviewServiceImpl.maskedName(q.getUser().getUsername()))
                .askerAvatarUrl(q.getUser().getAvatarUrl())
                .createdAt(q.getCreatedAt())
                .answer(q.getAnswer())
                .answeredAt(q.getAnsweredAt())
                .storeName(p.getStore() == null ? null : p.getStore().getName())
                .mine(viewerId != null && viewerId.equals(q.getUser().getId()))
                .build();
    }
}
