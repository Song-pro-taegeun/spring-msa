package com.msa.order.repository.inbox;

import com.msa.order.entity.inbox.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InboxEventRepository extends JpaRepository<InboxEvent, String> {
}
