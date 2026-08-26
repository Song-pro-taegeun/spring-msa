package com.msa.order.domain.redis;

public record InventoryReserveResult(
        boolean reservable,
        Integer quantity,
        long updateVersion
) {
}