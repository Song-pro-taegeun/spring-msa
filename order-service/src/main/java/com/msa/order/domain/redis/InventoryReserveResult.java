package com.msa.order.domain.redis;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record InventoryReserveResult(
        boolean reservable,
        Long productId,
        Long productOptionId,
        Integer quantity,
        long updateVersion,
        BigDecimal price,
        String currency
) {
}