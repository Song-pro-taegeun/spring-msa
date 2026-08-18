package com.msa.order.service.order;

import com.msa.common.kafka_event.TenantProductSnapshotEvent;
import com.msa.common.kafka_event.TenantProductSnapshotPayload;
import com.msa.order.entity.order.OrderProductSnapshot;
import com.msa.order.repository.order.OrderProductSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderProductSnapshotSyncService {
    private final OrderProductSnapshotRepository orderProductSnapshotRepository;

    @Transactional
    public void createProductSnapshot(TenantProductSnapshotEvent event){
        // 추가 작업 필요 내용
        // 멱등성 체크 로직 필요(inbox 도입)
        // 이벤트 발행 시 updateVersion 추가 해당 컬럼 기록


        List<OrderProductSnapshot> saveData = new ArrayList<>();
        List<TenantProductSnapshotPayload> payloads = event.getPayloads();
        payloads.forEach(data->{
            OrderProductSnapshot obj = OrderProductSnapshot.create(
                    data.getProductOptionId(),
                    data.getProductId(),
                    data.getProductName(),
                    data.getOptionName(),
                    data.getCurrency(),
                    data.getPrice()
            );
            saveData.add(obj);
        });
        orderProductSnapshotRepository.saveAll(saveData);
    }
}
