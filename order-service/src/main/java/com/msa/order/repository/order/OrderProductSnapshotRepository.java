package com.msa.order.repository.order;

import com.msa.order.entity.order.OrderProductSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderProductSnapshotRepository extends JpaRepository<OrderProductSnapshot, Long> {
}
