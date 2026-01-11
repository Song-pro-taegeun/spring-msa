package com.msa.auth.kafka.listener;

import com.msa.auth.kafka.internal.TenantProvisionedInternalEvent;
import com.msa.auth.kafka.producer.AuthEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Kafka 발행순서 2(이후 Producer)
 * Kafka 이벤트 실제 발행 리스너
 * 리스너는 이벤트 단위로 분리하는 것이 좋다(책임을 전가하지 않고 해당 클래스에서만 정의)
 */

/**
 * 해당 리스너는 Spring ApplicationEvent 인프라가 이벤트 타입 기반으로 자동 연결해서 호출한다
 * # 이벤트 타입 - TenantProvisionedInternalEvent
 * # 즉, Spring이 이벤트 타입을 기준으로 발행자와 Listener를 자동으로 연결해주는 것임
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class TenantProvisionedEventListener {
    private final AuthEventProducer authEventProducer;
    @TransactionalEventListener(
            // AuthService.signUp() 트랜잭션이 “커밋 성공한 뒤” 스프링에서 해당 메서드를 호출해준다.
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false // 트랜잭션이 없으면 실행하지 않는다.
    )
    public void handleUserCreatedInternalEvent(TenantProvisionedInternalEvent internalEvent) {
        authEventProducer.publishUserCreated(internalEvent.event());
    }
}