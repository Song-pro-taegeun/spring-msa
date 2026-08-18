package com.msa.common.kafka_event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantProductSnapshotPayload {
    private Long productId;
    private Long productOptionId;
    private String productName;
    private String optionName;
    private String currency;
    private BigDecimal price;
}