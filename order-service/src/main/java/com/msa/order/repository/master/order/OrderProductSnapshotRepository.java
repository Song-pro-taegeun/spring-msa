package com.msa.order.repository.master.order;

import com.msa.order.entity.master.order.OrderProductSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface OrderProductSnapshotRepository extends JpaRepository<OrderProductSnapshot, Long> {
    @Modifying
    @Query("""
        update OrderProductSnapshot snapshot
           set snapshot.productId = :productId,
               snapshot.productName = :productName,
               snapshot.optionName = :optionName,
               snapshot.currency = :currency,
               snapshot.price = :price,
               snapshot.updateVersion = :updateVersion
         where snapshot.productOptionId = :productOptionId
           and snapshot.updateVersion < :updateVersion
    """)
    int updateIfNewer(
            @Param("productOptionId") Long productOptionId,
            @Param("productId") Long productId,
            @Param("productName") String productName,
            @Param("optionName") String optionName,
            @Param("currency") String currency,
            @Param("price") BigDecimal price,
            @Param("updateVersion") Long updateVersion
    );
}
