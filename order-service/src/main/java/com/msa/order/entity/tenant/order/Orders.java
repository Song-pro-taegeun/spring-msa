package com.msa.order.entity.tenant.order;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_orders_ordered_at", columnList = "ordered_at"),
                @Index(
                        name = "idx_orders_status_ordered_at",
                        columnList = "order_status, ordered_at"
                )
        }
)
public class Orders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_orders_user")
    )
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 30)
    private OrderStatus orderStatus;

    @CreationTimestamp
    @Column(name = "ordered_at", nullable = false, updatable = false)
    private LocalDateTime orderedAt;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Getter(AccessLevel.NONE)
    private List<OrderItems> orderItems = new ArrayList<>();

    private Orders(Users user) {
        this.user = user;
        this.orderStatus = OrderStatus.CREATED;
    }

    public static Orders create(Users user) {
        if (user == null) {
            throw new IllegalArgumentException("주문 사용자는 필수입니다.");
        }

        return new Orders(user);
    }

    public OrderItems addItem(
            Long productOptionId,
            Integer quantity,
            BigDecimal unitPrice,
            String currency,
            Long productUpdateVersion
    ) {
        OrderItems orderItem = OrderItems.create(
                this,
                productOptionId,
                quantity,
                unitPrice,
                currency,
                productUpdateVersion
        );

        orderItems.add(orderItem);
        return orderItem;
    }
}