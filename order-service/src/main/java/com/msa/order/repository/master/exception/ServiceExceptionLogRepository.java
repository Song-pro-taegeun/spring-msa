package com.msa.order.repository.master.exception;

import com.msa.order.entity.master.exception.ServiceExceptionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceExceptionLogRepository extends JpaRepository<ServiceExceptionLog, Long> {
}
