package com.msa.order.repository.dlq;

import com.msa.order.entity.dlq.DlqMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessageEntity, Long> {
    Optional<DlqMessageEntity> findByOriginalTopicAndOriginalPartitionAndOriginalOffset(String originalTopic, Integer originalPartition, Long originalOffset);
}
