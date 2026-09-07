package com.msa.order.redis.stream;

import org.springframework.data.redis.connection.stream.MapRecord;

import java.math.BigDecimal;
import java.util.Map;

public record OrderAcceptedStreamEvent(
        String eventId,
        String tenantKey,
        String userId,
        Long productId,
        Long productOptionId,
        Integer quantity,
        BigDecimal price,
        String currency,
        Long updateVersion,
        Long acceptedAt
) {
    public static OrderAcceptedStreamEvent from(
            MapRecord<String, String, String> record
    ) {
        Map<String, String> values = record.getValue();

        return new OrderAcceptedStreamEvent(
                required(values, "eventId"),
                required(values, "tenantKey"),
                required(values, "userId"),
                parseLong(values, "productId"),
                parseLong(values, "productOptionId"),
                parseInteger(values, "quantity"),
                parseBigDecimal(values, "price"),
                required(values, "currency"),
                parseLong(values, "updateVersion"),
                parseLong(values, "acceptedAt")
        );
    }

    private static String required(Map<String, String> values, String field) {
        String value = values.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Redis 주문 Stream 필드가 없습니다: " + field);
        }
        return value;
    }

    private static Long parseLong(Map<String, String> values, String field) {
        try {
            return Long.valueOf(required(values, field));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Redis 주문 Stream 숫자 필드 형식이 잘못되었습니다: " + field,
                    e
            );
        }
    }

    private static Integer parseInteger(Map<String, String> values, String field) {
        try {
            return Integer.valueOf(required(values, field));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Redis 주문 Stream 숫자 필드 형식이 잘못되었습니다: " + field,
                    e
            );
        }
    }

    private static BigDecimal parseBigDecimal(Map<String, String> values, String field) {
        try {
            return new BigDecimal(required(values, field));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Redis 주문 Stream 가격 필드 형식이 잘못되었습니다: " + field,
                    e
            );
        }
    }
}
