package com.msa.order.service.order;

import com.msa.common.kafka_event.ProductSnapshotEvent;
import com.msa.common.kafka_event.ProductSnapshotPayload;
import com.msa.order.entity.master.inbox.InboxEvent;
import com.msa.order.entity.master.order.OrderProductSnapshot;
import com.msa.order.repository.master.inbox.InboxEventRepository;
import com.msa.order.repository.master.order.OrderProductSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProductSnapshotSyncService {
    private final OrderProductSnapshotRepository orderProductSnapshotRepository;
    private final InboxEventRepository inboxEventRepository;

    @Transactional(transactionManager = "masterTransactionManager")
    public void createProductSnapshot(ProductSnapshotEvent event){
        // 1. 처리된 이벤트인지 확인(이벤트 멱등성)
        if (inboxEventRepository.existsById(event.getEventId())) {
            return;
        }

        // 2. 스냅샷 저장
        for (ProductSnapshotPayload payload : event.getPayloads()) {
            // 다른 트랜잭션에서 동시에 처리되어 최종 이벤트 반영 순서가 꼬일 수 있으므로 조건부 업데이트 진행(원자적 갱신)
            int updated = orderProductSnapshotRepository.updateIfNewer(
                    payload.getProductOptionId(),
                    payload.getProductId(),
                    payload.getProductName(),
                    payload.getOptionName(),
                    payload.getCurrency(),
                    payload.getPrice(),
                    payload.getUpdateVersion()
            );

            // 업데이트 되지 않았을 때,
            if (updated == 0) {
                boolean exists = orderProductSnapshotRepository.existsById(payload.getProductOptionId());

                // 제품이 없었다면, 기존 스냅샷이 없는 경우 최초 데이터를 저장
                if (!exists) {
                    orderProductSnapshotRepository.save(
                            OrderProductSnapshot.create(
                                    payload.getProductOptionId(),
                                    payload.getProductId(),
                                    payload.getProductName(),
                                    payload.getOptionName(),
                                    payload.getCurrency(),
                                    payload.getPrice(),
                                    payload.getUpdateVersion()
                            )
                    );
                }
                // 제품이 존재하는데, 업데이트가 되지 않았을 때
                // 1. 동일 버전 이벤트가 다시 도착할 때(Inbox도입 후 중복으로 판단하여 처리 종료될 것임 참고)
                // 2. 이전 버전 이벤트가 늦게 도착할 때
                // 3. 다른 트랜잭션이 더 최신 버전을 반영할 때
                else {
                    log.debug("Order Service Product Snapshot 최신으로 반영된 제품이 존재. payload ={}", payload
                    );
                }
            }
        }

        // 3. inbox 기록
        inboxEventRepository.save(
                InboxEvent.create(
                        event.getEventId(),
                        event.getEventType().name()
                )
        );
    }
}
