package com.msa.order.config;

import com.msa.order.redis.stream.OrderAcceptedStreamConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.UUID;

@Slf4j
@Configuration
@EnableConfigurationProperties(OrderRedisStreamProperties.class)
public class OrderRedisStreamConfig {

    @Bean
    public ThreadPoolTaskExecutor orderRedisStreamTaskExecutor(OrderRedisStreamProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Consumer 수와 동일한 개수의 전용 스레드를 구성한다.
        // consumer-count가 16이면 Consumer 16개가 각각 polling, DB 저장, XACK을 담당한다.
        executor.setCorePoolSize(properties.getConsumerCount());
        executor.setMaxPoolSize(properties.getConsumerCount());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("order-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> orderStreamListenerContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            OrderRedisStreamProperties properties,
            OrderAcceptedStreamConsumer consumer,
            @Qualifier("orderRedisStreamTaskExecutor")
            TaskExecutor orderRedisStreamTaskExecutor
    ) {
        createConsumerGroup(redisTemplate, properties);

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(properties.getPollTimeout())
                        .batchSize(properties.getBatchSize())
                        .executor(orderRedisStreamTaskExecutor)
                        .errorHandler(error -> log.error("Redis Stream polling 실패", error))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        // 같은 Consumer Group에 고유한 이름의 Consumer를 consumerCount만큼 등록
        String instanceId = UUID.randomUUID().toString();
        for (int index = 1; index <= properties.getConsumerCount(); index++) {
            String consumerName = properties.getGroup() + "-" + instanceId + "-" + index;
            container.receive(
                    Consumer.from(properties.getGroup(), consumerName),
                    StreamOffset.create(
                            properties.getKey(),
                            ReadOffset.lastConsumed() // Consumer Group이 다음 메시지를 읽도록
                    ),
                    consumer
            );

            log.info(
                    "Redis 주문 Stream Consumer 등록. stream={}, group={}, consumer={}",
                    properties.getKey(),
                    properties.getGroup(),
                    consumerName
            );
        }

        return container;
    }

    // Consumer Group 생성
    private void createConsumerGroup(
            StringRedisTemplate redisTemplate,
            OrderRedisStreamProperties properties
    ) {
        byte[] streamKey = redisTemplate.getStringSerializer()
                .serialize(properties.getKey());

        if (streamKey == null) {
            throw new IllegalStateException("Redis 주문 Stream 키를 직렬화할 수 없습니다.");
        }

        try {
            redisTemplate.execute((RedisCallback<String>) connection ->
                    connection.streamCommands().xGroupCreate(
                            streamKey, // Stream
                            properties.getGroup(), // Consumer Group
                            ReadOffset.from("0-0"), // 그룹 생성 전에 들어온 기존 메시지부터 처리
                            true // mk STREAM(Stream이 없으면 빈 Stream을 생성)
                    )
            );
            log.info(
                    "Redis 주문 Stream Consumer Group 생성. stream={}, group={}",
                    properties.getKey(),
                    properties.getGroup()
            );
        } catch (DataAccessException e) {
            if (!hasBusyGroupCause(e)) {
                throw e;
            }

            log.debug(
                    "Redis 주문 Stream Consumer Group이 이미 존재합니다. stream={}, group={}",
                    properties.getKey(),
                    properties.getGroup()
            );
        }
    }

    private boolean hasBusyGroupCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
