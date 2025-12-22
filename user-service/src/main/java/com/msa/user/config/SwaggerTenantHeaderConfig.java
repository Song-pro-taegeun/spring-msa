package com.msa.user.config;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 스웨거 모든 항목에 X-Tenant-Id 필드 세팅
@Configuration
public class SwaggerTenantHeaderConfig {
    @Bean
    public OperationCustomizer tenantHeaderCustomizer() {
        return (operation, handlerMethod) -> {

            operation.addParametersItem(
                    new Parameter()
                            .in("header")
                            .name("X-Tenant-Id")
                            .description("Tenant Identifier")
                            .schema(new StringSchema())
                            .required(false)
            );

            return operation;
        };
    }
}
