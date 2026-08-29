package com.msa.order.repository.master.order;

import com.msa.order.entity.master.order.TestProductSnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TestProductSnapshotRepository
        extends JpaRepository<TestProductSnapshot, Long> {

    /**
     * 비관적 락 방식.
     * 반드시 트랜잭션 안에서 호출해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select snapshot
          from TestProductSnapshot snapshot
         where snapshot.productOptionId = :productOptionId
    """)
    Optional<TestProductSnapshot> findByIdForUpdate(
            @Param("productOptionId") Long productOptionId
    );

    /**
     * 조건부 업데이트 방식.
     *
     * 반환값:
     * 1 = 재고 차감 성공
     * 0 = 상품이 없거나 재고 부족
     */
    @Modifying
    @Query("""
        update TestProductSnapshot snapshot
           set snapshot.stock = snapshot.stock - :quantity
         where snapshot.productOptionId = :productOptionId
           and snapshot.stock >= :quantity
    """)
    int decreaseStockConditionally(
            @Param("productOptionId") Long productOptionId,
            @Param("quantity") int quantity
    );
}