CREATE TABLE IF NOT EXISTS orders (
    order_id       BIGINT NOT NULL AUTO_INCREMENT COMMENT '주문 ID',
    user_id        VARCHAR(50) NOT NULL COMMENT '주문 사용자 ID',
    order_status   VARCHAR(30) NOT NULL COMMENT '주문 상태',
    ordered_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '주문 일시',
    PRIMARY KEY (order_id),
    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id)
        REFERENCES users (user_id),

    INDEX idx_orders_user_id (user_id),
    INDEX idx_orders_ordered_at (ordered_at),
    INDEX idx_orders_status_ordered_at (order_status, ordered_at)
) COMMENT='주문';

CREATE TABLE IF NOT EXISTS order_items (
    order_item_id     BIGINT NOT NULL AUTO_INCREMENT COMMENT '주문 제품 ID',
    order_id          BIGINT NOT NULL COMMENT '주문 ID',
    product_option_id BIGINT NOT NULL COMMENT '상품 옵션 ID',
    quantity          INT NOT NULL COMMENT '주문 수량',
    unit_price        DECIMAL(19, 4) NOT NULL COMMENT '주문 당시 개당 가격',
    total_price       DECIMAL(19, 4) NOT NULL COMMENT '주문 제품 총금액',
    currency          CHAR(3) NOT NULL COMMENT '통화 코드',
    product_update_version bigint(20) NOT NULL DEFAULT 0 COMMENT '상품 옵션 이벤트 변경 버전',
    PRIMARY KEY (order_item_id),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders (order_id)
        ON DELETE CASCADE,

    INDEX idx_order_items_order_id (order_id),
    INDEX idx_order_items_product_option_id (product_option_id)
) COMMENT='주문 제품';