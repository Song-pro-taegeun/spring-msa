package com.msa.product.entity.product;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_products_code_brand_name",
                        columnNames = {
                                "product_code",
                                "brand_name",
                                "product_name"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_products_status_created_at",
                        columnList = "status, created_at"
                ),
                @Index(
                        name = "idx_products_product_name",
                        columnList = "product_name"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Products {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Lob
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "brand_name", length = 100)
    private String brandName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private Products(String productCode, String productName, String description, String brandName, String createdBy) {
        this.productCode = productCode;
        this.productName = productName;
        this.description = description;
        this.brandName = brandName;
        this.status = ProductStatus.DRAFT;
        this.createdBy = createdBy;
    }

    public static Products create(String productCode, String productName, String description, String brandName, String createdBy) {
        if (productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("제품 코드는 필수입니다.");
        }

        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("제품명은 필수입니다.");
        }

        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("제품 등록 사용자는 필수입니다.");
        }

        return new Products(productCode, productName, description, brandName, createdBy);
    }
}
