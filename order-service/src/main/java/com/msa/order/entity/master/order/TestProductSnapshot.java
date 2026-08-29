package com.msa.order.entity.master.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "test_product_snapshot")
public class TestProductSnapshot {

    @Id
    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @Column(name = "stock", nullable = false)
    private int stock;

    private TestProductSnapshot(Long productOptionId, int stock) {
        this.productOptionId = productOptionId;
        this.stock = stock;
    }

    public static TestProductSnapshot create(
            Long productOptionId,
            int stock
    ) {
        if (productOptionId == null || productOptionId <= 0) {
            throw new IllegalArgumentException("상품 옵션 ID는 양수여야 합니다.");
        }

        if (stock < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다.");
        }

        return new TestProductSnapshot(productOptionId, stock);
    }

    public boolean hasEnoughStock(int quantity) {
        return stock >= quantity;
    }

    public void decrease(int quantity) {
        if (stock < quantity) {
            throw new IllegalStateException("재고가 부족합니다. stock=" + stock + ", requested=" + quantity);
        }
        stock -= quantity;
    }
}