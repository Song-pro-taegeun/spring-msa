package com.msa.product.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductNormalize {
    private Long productId;
    private Long productOptionId;
    private String productName;
    private String optionName;
    private String currency;
    private BigDecimal price;
}
