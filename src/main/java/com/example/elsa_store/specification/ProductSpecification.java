package com.example.elsa_store.specification;

import com.example.elsa_store.dto.request.ProductSearchRequest;
import com.example.elsa_store.entity.Product;
import com.example.elsa_store.entity.ProductVariant;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

public class ProductSpecification {

    public static Specification<Product> search(ProductSearchRequest req) {
        return (root, query, cb) -> {

            root.fetch("variants", JoinType.LEFT);
            root.fetch("category", JoinType.LEFT);
            query.distinct(true);

            Predicate predicate = cb.conjunction();

            // keyword
            if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
                predicate = cb.and(predicate,
                        cb.or(
                                cb.like(cb.lower(root.get("name")), "%" + req.getKeyword().toLowerCase() + "%"),
                                cb.like(cb.lower(root.get("description")), "%" + req.getKeyword().toLowerCase() + "%")
                        )
                );
            }

            // category
            if (req.getCategoryId() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("category").get("id"), req.getCategoryId())
                );
            }

            // price range
            if (req.getMinPrice() != null) {
                predicate = cb.and(predicate,
                        cb.greaterThanOrEqualTo(root.get("basePrice"), req.getMinPrice())
                );
            }

            if (req.getMaxPrice() != null) {
                predicate = cb.and(predicate,
                        cb.lessThanOrEqualTo(root.get("basePrice"), req.getMaxPrice())
                );
            }

            // status
            if (req.getStatus() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("status"), req.getStatus())
                );
            }

            return predicate;
        };
    }
}