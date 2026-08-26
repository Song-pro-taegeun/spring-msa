package com.msa.order.dto;

import lombok.Getter;

@Getter
public class OrderRequestPurchaseDto {
    private Long productOptionId;
    private Integer quantity;
    private Long requestUpdateVersion;
}
