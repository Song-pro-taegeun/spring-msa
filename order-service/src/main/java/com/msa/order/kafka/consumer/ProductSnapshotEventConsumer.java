package com.msa.order.kafka.consumer;

import com.msa.common.kafka_event.ProductSnapshotEvent;
import com.msa.order.service.order.OrderProductSnapshotSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductSnapshotEventConsumer {
    @Value("${spring.base-schema-name}")
    private String serviceName;
    private final TenantProvisionEventConsumer tenantProvisionEventConsumer;
    private final OrderProductSnapshotSyncService orderProductSnapshotSyncService;

    @KafkaListener(
            topics = "product-snapshot",
            groupId = "order-service",
            containerFactory = "kafkaListenerContainerFactory" // kafkaErrorHandler가 적용된 컨테이너팩토리, DLQ가 아닌 토픽은 모두 해당 컨테이너 팩토리를 사용해야한다.
    )
    public void consumeProductSnapshot(
            ConsumerRecord<String, String> record,
            Acknowledgment ack // 수동 offset 커밋용 객체(Config에서 AckMode.MANUAL 설정일 때만 주입 됨)
    ) {
        // kafka value 역직렬화 체크
        ProductSnapshotEvent event = tenantProvisionEventConsumer.deserialize(
                record,
                ProductSnapshotEvent.class
        );

        try {
            // 내 서비스가 아니면 리턴 (테넌트 프로비져닝 이벤트는 서비스별로 여러개의 메시지를 발행한다)
            if (!serviceName.equals(event.getServiceName())) {
                ack.acknowledge();
                return;
            }

            orderProductSnapshotSyncService.createProductSnapshot(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("OrderService - ProductSnapshot 처리 실패. topic={}, partition={}, offset={}, key={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    e
            );
            throw e; // ErrorHandler -> retry / DLQ
        }
    }
}
