package com.msa.auth.kafka.internal;

import com.msa.common.kafka_event.UserCreatedEvent;

/**
 * Kafka 발행순서 1(이후 UserCreatedEventListener)
 * Spring ApplicationEvent 시스템을 타기 위한 래퍼(wrapper)
 * “내부 이벤트” 타입을 만들어서, Listener가 정확히 이 타입만 잡아 처리하게 함
 * 1. 외부로 내보낼 “Kafka payload” (UserCreatedEvent)
 * 2. 내부에서 트랜잭션 이후 실행 제어를 위한 “Spring 이벤트” (UserCreatedInternalEvent)
 * 내부/외부 관심사가 섞이지 않음
 * Internal은 이벤트 단위로 분리하는 것이 좋다(책임을 전가하지 않고 해당 클래스에서만 정의)
 * 데이터 전달용 불변 이벤트이기에 record로 구성
 */
public record UserCreatedInternalEvent(UserCreatedEvent event) {
}
