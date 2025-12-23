package com.msa.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

// Spring Kafka Listener가 “수동 커밋(MANUAL ACK)” 방식으로 메시지를 처리하도록 만드는 설정 클래스
// @KafkaListener 어노테이션이 붙은 메서드가 ack.acknowledge()를 호출해야만 offset이 커밋되도록 설정
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    // Kafka Listener 컨테이너를 만들어준다
    // @KafkaListener 하나당 내부적으로 Listener Container를 하나 만듦
    // kafkaListenerContainerFactory Spring Kafka 기본 이름 규칙 -> 자동으로 해당 설정 적용
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory // application-common에 정의한 컨슈머 설정을 그대로 사용
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory); // Listener Container는 해당 Consumer 설정을 사용해서 Kafka와 통신

        // Listener 메서드 안에서 Acknowledgment.acknowledge()를 호출해야만 offset을 커밋
        // offset 커밋을 개발자가 직접 통제
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}
