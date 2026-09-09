package com.msa.auth.kafka.listener;

import com.msa.auth.kafka.internal.TenantProvisionedInternalEvent;
import com.msa.auth.kafka.producer.AuthEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

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