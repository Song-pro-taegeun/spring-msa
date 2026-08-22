package com.msa.product.event.internal;

public record InventoryItem(
        Long productOptionId,
        int quantity,
        long updateVersion
) {
}
