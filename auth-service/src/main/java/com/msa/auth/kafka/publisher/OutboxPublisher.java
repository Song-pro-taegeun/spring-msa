package com.msa.auth.kafka.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.auth.entity.outbox.OutboxEvent;
import com.msa.auth.service.outbox.OutboxService;
import com.msa.common.kafka_event.TenantProvisionedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 아웃박스 폴링 방식 퍼블리셔 스케쥴러
 * - 본인은 이벤트 발행 시 비즈니스 처리 로직에서 아웃박스 이벤트를 발행하지 않음
 * - 이유 1. 비즈니스 로직 실행 성공 -> 메시지 발행 -> 아웃박스 이벤트 기록 -> 아웃박스 이벤트 exception 발생 -> 발행된 메시지는 취소 되지 않음
 * - 이유 2. 폴링 방식을 사용하면, 개발자에 의해 시간에 따른 메시지 발행량을 조절할 수 있음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxService outboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 아웃박스 퍼블리쉬 스케쥴러
     */
    @Scheduled(fixedDelayString = "${spring.outbox.publisher.delay}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxService.findPendingEvents();

        // pending 결과 별로 이벤트 발행
        for (OutboxEvent outboxEvent : events) {
            publish(outboxEvent);
        }
    }

    private void publish(OutboxEvent outboxEvent) {
        try {

            // Json 문자열 역직렬화
            TenantProvisionedEvent event = objectMapper.readValue(
                    outboxEvent.getPayload(),
                    TenantProvisionedEvent.class
            );

            // 메시지 발행
            kafkaTemplate.send(
                    outboxEvent.getTopic(),
                    outboxEvent.getAggregateId(),
                    event
            ).whenComplete((result, exception) -> {
                // 성공 기록
                if (exception == null) {
                    outboxService.markPublished(
                            outboxEvent.getEventId()
                    );
                }
                // 재시도 및 실패 처리 프로세스
                else {
                    outboxService.handlePublishFailure(
                            outboxEvent.getEventId(),
                            exception.getMessage()
                    );
                }
            });
        }
        // Json 역직렬화 오류 시 재시도를 할 이유가 없으므로 바로 실패로 기록
        catch (JsonProcessingException exception) {
            log.error(
                    "Outbox payload 역직렬화 실패. eventId={}",
                    outboxEvent.getEventId(),
                    exception
            );

            // 실패 기록
            outboxService.markFailed(
                    outboxEvent.getEventId(),
                    exception.getMessage()
            );
        }
    }
}
