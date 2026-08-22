package com.msa.product.event.internal;

import java.util.List;

public record InventoryInitializedEvent(
        List<InventoryItem> items
) {
}
