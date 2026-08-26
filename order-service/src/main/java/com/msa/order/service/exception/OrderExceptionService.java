package com.msa.order.service.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.order.entity.exception.ServiceExceptionLog;
import com.msa.order.repository.exception.ServiceExceptionLogRepository;
import com.msa.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;

@RequiredArgsConstructor
@Service
public class OrderExceptionService{
    private final ServiceExceptionLogRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(
            transactionManager = "masterTransactionManager",
            propagation = Propagation.REQUIRES_NEW
    )
    public void recordException(
            String methodName,
            RuntimeException exception,
            Object requestPayload
    ) {
        ServiceExceptionLog exceptionLog = ServiceExceptionLog.create(
                TenantContext.get(),
                MDC.get("traceId"),
                methodName,
                exception.getClass().getName(),
                exception.getMessage(),
                convertStackTrace(exception),
                serializeRequest(requestPayload)
        );

        repository.save(exceptionLog);
    }

    private String serializeRequest(Object requestPayload) {
        try {
            return objectMapper.writeValueAsString(requestPayload);
        } catch (JsonProcessingException e) {
            return "{\"serializationFailed\":true}";
        }
    }

    private String convertStackTrace(Throwable exception) {
        StringWriter writer = new StringWriter();

        try (PrintWriter printWriter = new PrintWriter(writer)) {
            exception.printStackTrace(printWriter);
        }

        return writer.toString();
    }
}
