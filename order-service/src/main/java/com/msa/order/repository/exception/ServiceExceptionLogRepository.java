package com.msa.order.repository.exception;

import com.msa.order.entity.exception.ServiceExceptionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceExceptionLogRepository extends JpaRepository<ServiceExceptionLog, Long> {
}
