package com.msa.common.kafka_event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSnapshotEvent {
    private String eventId;
    private EventType eventType;
    private String serviceName;

    private List<ProductSnapshotPayload> payloads;
}
