package com.msa.order.config.kafka;

import com.msa.common.kafka_event.DlqMessage;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Retry 정책, DLQ로 보내는 기준 정의 클래스
 */
@Configuration
public class KafkaErrorHandlerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {

        // 시도 실패 시 retry 지정 1초 간격으로 1번 지정
        FixedBackOff backOff = new FixedBackOff(1000L, 1);

        /**
         * Spring Kafka의 retry는 “poll 단위 retry”이고
         * DLQ로 메시지를 빼내지 않는 한 Consumer는 같은 offset을 영원히 다시 시도한다.
         */
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler((record, ex) -> {

                    // 실제 예외 root
                    Throwable root = ExceptionUtils.getRootCause(ex);
                    Throwable target = root != null ? root : ex;

                    // Spring 기본 DLQ가 아닌, Custom DLQ 사용
                    DlqMessage<Object> dlqMessage =
                            new DlqMessage<>(
                                    record.topic(),
                                    record.partition(),
                                    record.offset(),
                                    // ex.getClass().getName(), // 컨슈머 레벨 ex 클래스
                                    // ex.getMessage(),         // 컨슈머 레벨 ex 메시지
                                    target.getClass().getName(),// 실제 예외 클래스
                                    target.getMessage(),        // 실제 예외 메시지
                                    ExceptionUtils.getStackTrace(ex),
                                    record.value()
                            );


                    /**
                     * value를 DLQ용 메시지로 교체 후
                     * recoverer.accept() 함수가 KafkaTemplate를 사용하여 메시지를 발행한다.
                     */
                    recoverer.accept(
                            new ConsumerRecord<>(
                                    record.topic(),
                                    record.partition(),
                                    record.offset(),
                                    record.key(),
                                    dlqMessage
                            ),
                            ex
                    );
                }, backOff);

        /**
         * DLQ로 전송했으면, Offset 커밋
         * 현재 AckMode.MANUAL 타입이라 poll 종료 시점 커밋이 진행되지만, 추후 모드를 변경하였을 때를 대비하여 true 로직을 넣는다
         * 일종의 약속이라고 생각하자
         */
        errorHandler.setAckAfterHandle(true);
        return errorHandler;
    }
}

