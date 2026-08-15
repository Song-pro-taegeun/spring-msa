package com.msa.product.entity.product;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_options",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_product_options_product_name",
                        columnNames = {
                                "product_id",
                                "option_name"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_product_options_product_status",
                        columnList = "product_id, status"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_product_options_product"
            )
    )
    private Products products;

    @Column(name = "option_name", nullable = false, length = 200)
    private String optionName;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductOptionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private ProductOption(Products products, String optionName, BigDecimal price, String currency) {
        this.products = products;
        this.optionName = optionName;
        this.price = price;
        this.currency = currency;
        this.status = ProductOptionStatus.ACTIVE;
    }

    public static ProductOption create(
            Products products,
            String optionName,
            BigDecimal price,
            String currency
    ) {
        if (products == null) {
            throw new IllegalArgumentException("제품은 필수입니다.");
        }

        if (optionName == null || optionName.isBlank()) {
            throw new IllegalArgumentException("제품 옵션명은 필수입니다.");
        }

        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("제품 옵션 가격은 0 이상이어야 합니다.");
        }

        if (currency == null) {
            throw new IllegalArgumentException("제품 통화 단위는 필수입니다.");
        }

        return new ProductOption(products, optionName, price, currency);
    }
}