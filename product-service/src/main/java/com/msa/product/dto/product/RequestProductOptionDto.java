package com.msa.product.dto.product;

import java.math.BigDecimal;

public record RequestProductOptionDto(
        String optionName,
        BigDecimal price,
        String currency,
        Integer totalQuantity
) {
}
