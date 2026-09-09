package com.msa.auth.kafka.internal;

import com.msa.common.kafka_event.TenantProvisionedEvent;

public record TenantProvisionedInternalEvent(TenantProvisionedEvent event) {
}
