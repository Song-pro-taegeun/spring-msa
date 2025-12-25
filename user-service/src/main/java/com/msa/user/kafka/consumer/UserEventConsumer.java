package com.msa.user.kafka.consumer;

import com.msa.common.kafka_event.UserCreatedEvent;
import com.msa.tenant.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class UserEventConsumer {
    /**
     * user-created 토픽을 구독
     */
    @KafkaListener(
            topics = "user-created",
            groupId = "user-service"
    )
    public void consumeUserCreated(
            ConsumerRecord<String, UserCreatedEvent> record,
            Acknowledgment ack // 수동 offset 커밋용 객체(Config에서 AckMode.MANUAL 설정일 때만 주입 됨)
    ) {
        Headers headers = record.headers();
        // 1. DLQ replay 메시지 차단(일종의 안전장치)
        if (headers.lastHeader("dlq-replayed") != null) {
            log.error("""
            DLQ replay 메시지 재수신 -> 처리 중단
            topic={}
            offset={}
            """, record.topic(), record.offset());

            ack.acknowledge(); // 여기서 끊어야 무한루프 종료
            return;
        }

        try {
            UserCreatedEvent event = record.value();
            // tenantKey 기반 멀티테넌시 설정
            TenantContext.set(event.getTenantKey());
            log.info("event: {}", event);
            int a = 1;
            int b = 0;
            int c = a/b;

            /**
             * ack.acknowledge()를 하지 않으면 어떤 현상이 발생하는가?(offset commit이 되지 않았을 때)
             * consumer 그룹은 구독하고 있는 토픽에서 발행한 end offset 만큼 Consumer 로직이 실행된다. (단, 특정 상황일 때,)
             * 특정상황 1. Consumer가 재시작될 때
             * 특정상황 2. 리밸런싱 발생 시
             * 특정상황 3. 예외 발생
             * 본인은 테스트를 위해 0으로 값을 나눠 ArithmeticException를 발생ㅎ 시킴
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
            UserCreatedEvent event = record.value();
            log.error("UserCreatedEvent 처리 실패: {}", event, e);
            throw e; // ErrorHandler → retry / DLQ
        } finally {
            TenantContext.clear();
        }
    }
}


/**
 * 내일 해야 할 것
 * 현재 구조는 DLQ 적재 -> 재 발행이었다(트러블슈팅)
 * 헌데 문제가 생김 -> DLQ 컨슈머가 원래 컨슈머에 다시 발행하기에 무한루프 발생
 *              -> 무한루프 막고자 헤더 및 환경변수로 로직 제어
 *              -> 재발행 로직이 명확하지 않고 수동으로 환경변수를 조작해야함
 *              -> 이를 방지하기위해 DLQ 재발행 로직을 RestAPI로 구성해야만 하고 DLQ 컨슈머를 따로 구성해야한다(?)
 *              -> 이 또한 오답이다 DLQ 컨슈머는 공통으로 하나로 구성하고, 토픽만 분리하자(DlqMessage 해당 클래스가 DLQ 공통 처리를 가능하게 한다.)
 *              ->
 *  앞으로 변경될 구조는
 *  - DLQ 관련 환경변수 지우기
 *  - 원본토픽에 헤더로 제어 지우기
 *  - DLQ 처리 컨슈머 구성하기(공통)
 *  - 토픽 단위 RestAPI 구성하기
 *  - UserDlqReplayConsumer 삭제하던가 수정하던가 하기(환경설정 어노테이션지우기)
 *  - DB에 DLQ를 적재하여 RestAPI에서 사용하기
 * 일단 커밋하고 수정하기
 */