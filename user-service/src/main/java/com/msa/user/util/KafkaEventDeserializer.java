package com.msa.user.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventDeserializer {
    private final ObjectMapper objectMapper;

    // 공통으로 사용할 것이기에 제네릭 타입으로 구현
    public <T> T deserialize(
            ConsumerRecord<String, String> record,
            Class<T> tClass
    ) {
        try {
            return objectMapper.readValue(
                    record.value(),
                    tClass
            );
        } catch (JsonProcessingException exception) {
            log.error(
                    "Kafka 이벤트 역직렬화 실패. topic={}, partition={}, offset={}, key={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    exception
            );

            throw new IllegalArgumentException(
                    tClass.getSimpleName() + " 역직렬화 실패",
                    exception
            );
        }
    }
}