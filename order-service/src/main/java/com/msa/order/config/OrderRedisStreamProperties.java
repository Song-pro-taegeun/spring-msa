package com.msa.order.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "order.redis-stream")
public class OrderRedisStreamProperties {
    private String key = "order:accepted:stream"; // XADD Stream key
    private String group = "order-db-writer"; // 주문 DB 저장을 담당하는 Consumer Group
    private Duration pollTimeout = Duration.ofSeconds(1); // 메시지가 없을 때 Redis에서 최대 1초 대기
    private int batchSize = 10; // 한 번에 최대 10개 조회
    private int consumerCount = 16; // 같은 Consumer Group에 등록할 Consumer 수
}
