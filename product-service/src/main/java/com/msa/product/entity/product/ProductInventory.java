package com.msa.product.entity.product;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInventory {

    @Id
    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @MapsId // ProductOption에서 product_option_id 식별자를 제공
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_option_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_product_inventory_option"
            )
    )
    private ProductOption productOption;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private Integer soldQuantity;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 생성자 초기화(검증된 값으로 필드 초기화)
    private ProductInventory(
            ProductOption productOption,
            Integer totalQuantity
    ) {
        this.productOption = productOption;
        this.totalQuantity = totalQuantity;
        this.soldQuantity = 0;
    }

    // 정적 생성 메써드(외부에 공개되는 생성 진입점과 유효성 검증)
    public static ProductInventory create(ProductOption productOption, Integer totalQuantity) {
        if (productOption == null) {
            throw new IllegalArgumentException("제품 옵션은 필수입니다.");
        }

        if (totalQuantity == null || totalQuantity < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다.");
        }
        return new ProductInventory(productOption, totalQuantity);
    }

}