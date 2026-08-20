package com.msa.common.kafka_event;

import lombok.*;

import java.math.BigDecimal;

@Getter
@ToString
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
    private Long updateVersion;
}