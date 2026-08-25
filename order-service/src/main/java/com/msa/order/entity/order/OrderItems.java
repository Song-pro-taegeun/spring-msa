package com.msa.order.entity.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "order_items",
        indexes = {
                @Index(
                        name = "idx_order_items_order_id",
                        columnList = "order_id"
                ),
                @Index(
                        name = "idx_order_items_product_option_id",
                        columnList = "product_option_id"
                )
        }
)
public class OrderItems {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_order_items_order")
    )
    private Orders order;

    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(
            name = "unit_price",
            nullable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal unitPrice;

    @Column(
            name = "total_price",
            nullable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal totalPrice;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "product_update_version", nullable = false)
    private Long productUpdateVersion;

    private OrderItems(
            Orders order,
            Long productOptionId,
            Integer quantity,
            BigDecimal unitPrice,
            String currency,
            Long productUpdateVersion
    ) {
        this.order = order;
        this.productOptionId = productOptionId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.currency = currency;
        this.productUpdateVersion = productUpdateVersion;
    }

    static OrderItems create(
            Orders order,
            Long productOptionId,
            Integer quantity,
            BigDecimal unitPrice,
            String currency,
            Long productUpdateVersion
    ) {
        if (order == null) {
            throw new IllegalArgumentException("주문은 필수입니다.");
        }

        if (productOptionId == null || productOptionId <= 0) {
            throw new IllegalArgumentException("상품 옵션 ID는 양수여야 합니다.");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
        }

        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("상품 가격은 0 이상이어야 합니다.");
        }

        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException(
                    "통화 코드는 대문자 3자리여야 합니다."
            );
        }

        if (productUpdateVersion == null || productUpdateVersion < 0) {
            throw new IllegalArgumentException(
                    "상품 변경 버전은 0 이상이어야 합니다."
            );
        }

        return new OrderItems(
                order,
                productOptionId,
                quantity,
                unitPrice,
                currency,
                productUpdateVersion
        );
    }
}