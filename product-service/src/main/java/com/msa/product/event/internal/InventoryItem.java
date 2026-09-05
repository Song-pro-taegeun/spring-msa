package com.msa.product.event.internal;

import java.math.BigDecimal;

public record InventoryItem(
        Long productId,
        Long productOptionId,
        int quantity,
        BigDecimal price,
        String currency,
        long updateVersion
) {
}
