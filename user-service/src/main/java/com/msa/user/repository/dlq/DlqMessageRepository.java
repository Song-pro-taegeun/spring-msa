package com.msa.user.repository.dlq;

import com.msa.user.entity.admin.DlqMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessageEntity, Long> {
}
