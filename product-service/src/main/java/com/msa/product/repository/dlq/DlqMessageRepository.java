package com.msa.product.repository.dlq;

import com.msa.product.entity.dlq.DlqMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessageEntity, Long> {
}
