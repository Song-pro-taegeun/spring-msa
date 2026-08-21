package com.msa.product.kafka.publisher;

import com.msa.product.entity.outbox.OutboxEvent;
import com.msa.product.service.outbox.OutboxService;
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

    /**
     * 아웃박스 퍼블리쉬 스케쥴러
     */
    @Scheduled(fixedDelayString = "${spring.outbox.publisher.delay}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxService.claimPendingEvents();

        // pending 결과 별로 이벤트 발행
        for (OutboxEvent outboxEvent : events) {
            try {
                publish(outboxEvent);
            } catch (Exception exception) {
                // Kafka 동기 예외(발행 전 서버가 down됐거나 카프카 통신 실패일 경우 재시도 및 실패 처리)
                // - 환경 변수에서 설정한 max.block.ms: 3000를 기준으로 3초간 응답하지 않았을 때 발행실패를 기록
                // - 재시도 지수 백오프 시간
                // KafkaTemplate.send() 호출 과정에서 발생한
                // Kafka 동기 예외 및 기타 동기 RuntimeException 처리
                log.error(
                        "Outbox 동기 발행 처리 실패. eventId={}",
                        outboxEvent.getEventId(),
                        exception
                );

                recordFailureSafely(outboxEvent, exception);
            }
        }
    }

    private void publish(OutboxEvent outboxEvent) {
        // 1. Kafka 발행
        kafkaTemplate.send(
                outboxEvent.getTopic(),
                outboxEvent.getAggregateId(),
                outboxEvent.getPayload()
        ).whenComplete((result, exception) -> {
            // 2. Kafka 비동기 발행 결과
            if (exception == null) {
                try {
                    outboxService.markPublished(outboxEvent.getEventId());
                } catch (Exception statusException) {
                    log.error(
                            "Kafka 발행 성공 후 Outbox 상태 변경 실패. eventId={}",
                            outboxEvent.getEventId(),
                            statusException
                    );
                }
            }
            // Kafka 비동기 예외(발행 후 서버거 down 통신 끊김 등)
            else {
                log.error(
                        "Kafka 비동기 발행 실패. eventId={}",
                        outboxEvent.getEventId(),
                        exception
                );

                recordFailureSafely(outboxEvent, exception);
            }
        });
    }

    private void recordFailureSafely(
            OutboxEvent outboxEvent,
            Throwable exception
    ) {
        try {
            outboxService.handlePublishFailure(
                    outboxEvent.getEventId(),
                    exception.getMessage()
            );
        } catch (Exception recordingException) {
            log.error(
                    "Outbox 실패 상태 기록 실패. eventId={}",
                    outboxEvent.getEventId(),
                    recordingException
            );
        }
    }
}
