package com.msa.order.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class OrderRequestPurchaseDto {
    private Long productOptionId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String currency;

    private Long requestUpdateVersion;
}
