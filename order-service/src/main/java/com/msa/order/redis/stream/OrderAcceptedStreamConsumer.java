package com.msa.order.redis.stream;

import com.msa.order.config.OrderRedisStreamProperties;
import com.msa.order.service.order.OrderStreamCommandService;
import com.msa.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAcceptedStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {
    private final OrderStreamCommandService orderStreamCommandService;
    private final StringRedisTemplate redisTemplate;
    private final OrderRedisStreamProperties properties;

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String eventId = record.getValue().get("eventId");

        try {
            OrderAcceptedStreamEvent event = OrderAcceptedStreamEvent.from(record);

            // DB 트랜잭션을 시작하기 전에 메시지의 tenantKey를 설정
            // 접속정보 - 테넌트
            TenantContext.set(event.tenantKey());
            boolean created = orderStreamCommandService.createOrder(event);

            // createOrder()의 트랜잭션 커밋이 끝난 뒤에만 ACK한다.
            Long acknowledged = redisTemplate.opsForStream().acknowledge(
                    properties.getKey(),
                    properties.getGroup(),
                    record.getId()
            );

            if (acknowledged == null || acknowledged != 1L) {
                log.warn(
                        "Redis 주문 Stream ACK 결과가 예상과 다릅니다. streamId={}, eventId={}, acknowledged={}",
                        record.getId(), event.eventId(), acknowledged
                );
                return;
            }

            log.debug(
                    "Redis 주문 Stream 처리 완료. streamId={}, eventId={}, created={}",
                    record.getId(), event.eventId(), created
            );
        } catch (Exception e) {
            // ACK하지 않으므로 메시지는 Pending에 남고 이후 XAUTOCLAIM 재처리 대상이 된다.
            log.error(
                    "Redis 주문 Stream 처리 실패. streamId={}, eventId={}",
                    record.getId(), eventId, e
            );
        } finally {
            TenantContext.clear();
        }
    }
}
