package com.msa.common.tenant.provision;

public enum TenantProvisionStatus {
    PROCESSING, // 단계의 프로비져닝이 정상 수행 상태
    COMPLETED,  // 모든 프로비저닝 단계가 정상 완료된 상태, 같은 eventId 또는 tenantKey 이벤트가 다시 오면 skip 가능
    FAILED      // 중간 단계에서 실패한 상태, Kafka retry/DLQ/운영자 재처리 대상
}
