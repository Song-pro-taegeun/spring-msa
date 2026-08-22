package com.msa.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * lua script 리소스 파일 Bean 등록 설정파일
 */
@Configuration
public class RedisScriptConfig {

    // redis 재고 초기화 script Bean
    @Bean
    public RedisScript<Long> initializeInventoryScript() {
        return createScript(
                "redis/scripts/inventory_initialize.lua",
                Long.class
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
