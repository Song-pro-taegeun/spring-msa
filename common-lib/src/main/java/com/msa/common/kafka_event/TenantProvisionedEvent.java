package com.msa.common.kafka_event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantProvisionedEvent {
    // private String eventId;
    private String serviceName;
    private String tenantKey;
    private String userId;
    private String userName;
    private byte[] passwordEnc;
    private byte[] encIv;
    private String regDtm;

}