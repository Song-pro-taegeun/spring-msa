package com.msa.product.config.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

/**
 * KafkaListener 동작 설정 클래스(컨테이너 레벨에서 정의)
 * @KafkaListener 어노테이션이 붙은 메서드가 ack.acknowledge()를 호출해야만 offset이 커밋되도록 설정
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    /**
     * Kafka Listener 컨테이너를 만들어준다
     * @KafkaListener 하나당 내부적으로 Listener Container를 하나 만듦
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory, // application-common에 정의한 컨슈머 설정을 그대로 사용
            DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        // application-common에 정의한 컨슈머 설정을 그대로 사용
        factory.setConsumerFactory(consumerFactory);

        /**
         * Listener 메서드 안에서 Acknowledgment.acknowledge()를 호출해야만 offset을 커밋
         * offset 커밋을 개발자가 직접 통제
         * type: MANUAL - 지연 커밋(poll 종료 시 offset 커밋)
         */
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL);

        // Consumer Listener Exception 발생 시 kafkaErrorHandler 연계를 위함
        factory.setCommonErrorHandler(kafkaErrorHandler);

        return factory;
    }

    /**
     * DLQ 전용 Kafka Listener 컨테이너를 만들어준다
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> dlqPersistKafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // 수동 ack 유지
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL);

        // DLQ ErrorHandler 미설정
        factory.setCommonErrorHandler(null);

        return factory;
    }
}
