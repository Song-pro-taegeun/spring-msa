package com.msa.order.entity.master.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_product_snapshot")
public class OrderProductSnapshot {
    @Id
    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "option_name", nullable = false, length = 200)
    private String optionName;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "update_version", nullable = false)
    private Long updateVersion;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private OrderProductSnapshot(Long productOptionId,
                                 Long productId,
                                 String productName,
                                 String optionName,
                                 String currency,
                                 BigDecimal price,
                                 Long updateVersion){
        this.productOptionId = productOptionId;
        this.productId = productId;
        this.productName = productName;
        this.optionName = optionName;
        this.currency = currency;
        this.price = price;
        this.updateVersion = updateVersion;
    }

    public static OrderProductSnapshot create(Long productOptionId,
                                              Long productId,
                                              String productName,
                                              String optionName,
                                              String currency,
                                              BigDecimal price,
                                              Long updateVersion){
        if (productOptionId == null || productOptionId <= 0) {
            throw new IllegalArgumentException("상품 옵션 ID는 양수여야 합니다.");
        }

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("상품 ID는 양수여야 합니다.");
        }

        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }

        if (productName.length() > 200) {
            throw new IllegalArgumentException("상품명은 200자를 초과할 수 없습니다.");
        }

        if (optionName == null || optionName.isBlank()) {
            throw new IllegalArgumentException("옵션명은 필수입니다.");
        }

        if (optionName.length() > 200) {
            throw new IllegalArgumentException("옵션명은 200자를 초과할 수 없습니다.");
        }

        if (currency == null || !currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("통화 코드는 대문자 3자리여야 합니다.");
        }

        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다.");
        }

        if (updateVersion == null || updateVersion < 0) {
            throw new IllegalArgumentException("버전은 0 이상이어야 합니다.");
        }

        return new OrderProductSnapshot(productOptionId, productId, productName, optionName, currency, price, updateVersion);
    }
}
