package com.msa.user.kafka.consumer;

import com.msa.common.kafka_event.TenantProvisionedEvent;
import com.msa.user.service.user.TenantSchemaService;
import com.msa.user.util.KafkaEventDeserializer;
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
public class TenantProvisionEventConsumer {
    @Value("${spring.base-schema-name}")
    private String serviceName;
    private final TenantSchemaService tenantSchemaService;
    private final KafkaEventDeserializer kafkaEventDeserializer;

    @KafkaListener(
            topics = "tenant-provision",
            groupId = "user-service",
            containerFactory = "kafkaListenerContainerFactory" // kafkaErrorHandler가 적용된 컨테이너팩토리, DLQ가 아닌 토픽은 모두 해당 컨테이너 팩토리를 사용해야한다.
    )
    public void consumeUserCreated(
            ConsumerRecord<String, String> record,
            Acknowledgment ack // 수동 offset 커밋용 객체(Config에서 AckMode.MANUAL 설정일 때만 주입 됨)
    ) {
        // kafka value 역직렬화 체크
        TenantProvisionedEvent event = kafkaEventDeserializer.deserialize(
                record,
                TenantProvisionedEvent.class
        );

        try {
            // 내 서비스가 아니면 리턴 (테넌트 프로비져닝 이벤트는 서비스별로 여러개의 메시지를 발행한다)
            if (!serviceName.equals(event.getServiceName())) {
                ack.acknowledge();
                return;
            }

//            int a = 1/0;
            tenantSchemaService.provision(event);
//            int a = 1/0;

            /**
             * ack.acknowledge()를 하지 않으면 어떤 현상이 발생하는가?(offset commit이 되지 않았을 때)
             * consumer 그룹은 구독하고 있는 토픽에서 발행한 end offset 만큼 Consumer 로직이 실행된다. (단, 특정 상황일 때,)
             * 특정상황 1. Consumer가 재시작될 때
             * 특정상황 2. 리밸런싱 발생 시
             * 특정상황 3. 예외 발생
             * 본인은 테스트를 위해 0으로 값을 나눠 ArithmeticException를 발생시킴
             * current offset 다음부터 end offset까지 재실행하는 것을 확인함.
             */
             ack.acknowledge(); // 정상 처리 후 offset까지 Kafka에 커밋

            /**
             * Next Step!!!!
             * Exception 발생 시 재시도 -> 실패 -> 재시도 -> 실패 등 무한 반복문에 빠지게 된다
             * 이 메시지 하나로 파티션이 막히고, 정상 메시지 전부 처리를 하지 못하는 현상이 발생함.(Poison Message 라고 명명)
             * ### DLQ(Dead Letter Queue)의 도입이 필요 - DLQ란 여러 번 처리에 실패한 메시지를 격리하는 kafka 토픽
             * 예시 -> 재시도 3회 발생 시 DLQ로 이동(시스템 전체를 살리기위한 격리소!)
             * DLQ는 "기존 topic"/ "partition, offset" / "exception 메시지", "stack trace" 정보로 구성되면 좋다
             * DLQ는 Consumer 단위 이벤트 처리 실패 격리 용도
             */
        } catch (Exception e) {
            log.error("TenantProvisionedEvent 처리 실패. topic={}, partition={}, offset={}, key={}",
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