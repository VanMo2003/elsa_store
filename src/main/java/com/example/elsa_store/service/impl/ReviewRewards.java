package com.example.elsa_store.service.impl;

import com.example.elsa_store.constant.CoinTransactionType;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.User;
import com.example.elsa_store.repository.CoinTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thưởng xu cho đánh giá: viết đánh giá đầu tiên cho một sản phẩm được {@code reviewReward} xu, lần đầu thêm ảnh được
 * thêm {@code photoReward} xu. Mỗi khách nhận mỗi khoản một lần cho mỗi sản phẩm (xoá rồi viết lại không nhận lại);
 * xoá đánh giá không thu lại xu.
 */
@Component
public class ReviewRewards {

    private final CoinWallet wallet;
    private final CoinTransactionRepository repository;
    private final int reviewReward;
    private final int photoReward;

    public ReviewRewards(CoinWallet wallet,
                         CoinTransactionRepository repository,
                         @Value("${app.coins.review-reward:100}") int reviewReward,
                         @Value("${app.coins.review-photo-reward:200}") int photoReward) {
        this.wallet = wallet;
        this.repository = repository;
        this.reviewReward = Math.max(0, reviewReward);
        this.photoReward = Math.max(0, photoReward);
    }

    /** Số xu khách còn nhận được nếu viết đánh giá (và thêm ảnh) cho sản phẩm này. */
    public int claimable(User user, Product product) {
        if (!CoinWallet.eligible(user) || product == null) {
            return 0;
        }
        int total = 0;
        if (!claimed(user, product, CoinTransactionType.REVIEW_REWARD)) {
            total += reviewReward;
        }
        if (!claimed(user, product, CoinTransactionType.PHOTO_REWARD)) {
            total += photoReward;
        }
        return total;
    }

    /** Sau khi viết / sửa đánh giá; trả số xu vừa cộng (0 nếu đã nhận trước đó). */
    public int afterReview(User user, Product product) {
        return grant(user, product, CoinTransactionType.REVIEW_REWARD, reviewReward,
                "Thưởng đánh giá " + product.getName());
    }

    /** Sau khi thêm ảnh vào đánh giá; trả số xu vừa cộng. */
    public int afterPhotos(User user, Product product) {
        return grant(user, product, CoinTransactionType.PHOTO_REWARD, photoReward,
                "Thưởng đánh giá có ảnh " + product.getName());
    }

    private int grant(User user, Product product, CoinTransactionType type, int amount, String note) {
        if (amount <= 0 || !CoinWallet.eligible(user) || product == null || claimed(user, product, type)) {
            return 0;
        }
        wallet.credit(user, amount, type, product, note);
        return amount;
    }

    private boolean claimed(User user, Product product, CoinTransactionType type) {
        return repository.existsByUser_IdAndProduct_IdAndType(user.getId(), product.getId(), type);
    }
}
