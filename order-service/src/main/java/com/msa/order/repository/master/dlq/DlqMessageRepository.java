package com.msa.order.repository.master.dlq;

import com.msa.order.entity.master.dlq.DlqMessageEntity;
import com.msa.order.entity.master.dlq.DlqStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessageEntity, Long> {
    Optional<DlqMessageEntity> findByOriginalTopicAndOriginalPartitionAndOriginalOffset(String originalTopic, Integer originalPartition, Long originalOffset);

    @Modifying
    @Query("""
        UPDATE DlqMessageEntity d
           SET d.status = :replaying
         WHERE d.id = :id
           AND d.status IN :replayableStatuses
        """)
    int markReplayingIfReplayable(@Param("id") Long id, @Param("replaying") DlqStatus replaying, @Param("replayableStatuses") Collection<DlqStatus> replayableStatuses);
}
