package com.msa.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * lua script 리소스 파일 Bean 등록 설정파일
 */
@Configuration
public class RedisScriptConfig {

    // redis 재고 감소 script Bean
    @Bean
    public RedisScript<List> reserveInventoryScript() {
        return createScript(
                "redis/scripts/inventory_reserve.lua",
                List.class
        );
    }

    // redis 재고 보상 script Bean
    @Bean
    public RedisScript<Long> compensateInventoryScript() {
        return createScript(
                "redis/scripts/compensate_inventoryScript.lua",
                Long.class
        );
    }

    // redis 재고감소 + 주문 + 원장 + 에빈트 멱등성 기록 등 script Bean(Only Redis)
    @Bean
    public RedisScript<List> acceptRedisOrderScript() {
        return createScript(
                "redis/scripts/order_accept.lua",
                List.class
        );
    }

    private <T> RedisScript<T> createScript(
            String path,
            Class<T> resultType
    ) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }
}
