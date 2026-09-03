package com.msa.product.service.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.common.kafka_event.EventType;
import com.msa.common.kafka_event.ProductSnapshotEvent;
import com.msa.common.kafka_event.ProductSnapshotPayload;
import com.msa.product.config.TenantProductSnapshotProperties;
import com.msa.product.domain.ProductNormalize;
import com.msa.product.dto.product.RequestProductDto;
import com.msa.product.dto.product.RequestProductOptionDto;
import com.msa.product.entity.outbox.OutboxEvent;
import com.msa.product.entity.product.ProductInventory;
import com.msa.product.entity.product.ProductOption;
import com.msa.product.entity.product.Products;
import com.msa.product.event.internal.InventoryInitializedEvent;
import com.msa.product.event.internal.InventoryItem;
import com.msa.product.repository.outbox.OutboxEventRepository;
import com.msa.product.repository.product.ProductInventoryRepository;
import com.msa.product.repository.product.ProductOptionRepository;
import com.msa.product.repository.product.ProductsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@RequiredArgsConstructor
@Service
public class ProductService {
    private final ApplicationEventPublisher eventPublisher;
    private final ProductsRepository productsRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final OutboxEventRepository outboxEventRepository;

    private final TenantProductSnapshotProperties productSnapshotProperties;
    private final ObjectMapper objectMapper;

    private record ProductOptionInventoryCreationResult(
            List<ProductOption> options,
            List<ProductInventory> inventories
    ) {}

    @Transactional
    public void createProduct(RequestProductDto requestProductDto){
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // 1. 제품 생성
        Products createProductResult = saveProduct(requestProductDto, userId);

        // 2. 제품 옵션 및 인벤토리 생성
        ProductOptionInventoryCreationResult creationOptionInventoryResult =
                saveProductOptionAndInventory(createProductResult, requestProductDto);
        List<ProductOption> createProductOptions = creationOptionInventoryResult.options();

        // 3. 제품 옵션 데이터 normalize
        List<ProductNormalize> productNormalizeList = productNormalizeProcess(createProductResult, createProductOptions);

        // 4. 데이터 복제와 최종적 일관성이 필요한 서비스에 이벤트 발행
        for (String serviceName : productSnapshotProperties.getServices()) {
            String eventId = UUID.randomUUID().toString();

            // 페이로드 내 배열 데이터 정규화
            List<ProductSnapshotPayload> payloads = new ArrayList<>();
            productNormalizeList.forEach((data)->{
                ProductSnapshotPayload payloadData = ProductSnapshotPayload.builder()
                        .productId(data.getProductId())
                        .productOptionId(data.getProductOptionId())
                        .productName(data.getProductName())
                        .optionName(data.getOptionName())
                        .currency(data.getCurrency())
                        .price(data.getPrice())
                        .updateVersion(data.getUpdateVersion())
                        .build();
                payloads.add(payloadData);
            });

            // 이벤트 페이로드 생성
            ProductSnapshotEvent payload = ProductSnapshotEvent.builder()
                    .eventId(eventId)
                    .eventType(EventType.PRODUCT_SNAPSHOT)
                    .serviceName(serviceName)
                    .payloads(payloads)
                    .build();

            String eventPayload;
            try {
                eventPayload = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(
                        "제품 스냅샷 이벤트 직렬화 실패. eventId=" + eventId,
                        e
                );
            }

            outboxEventRepository.save(
                    OutboxEvent.builder()
                            .eventId(eventId)
                            .aggregateId("MASTER_SCHEMA")
                            .eventType(EventType.PRODUCT_SNAPSHOT.name())
                            .serviceName(serviceName)
                            .topic("product-snapshot")
                            .payload(eventPayload)
                            .retryCount(0)
                            .build()
            );
        }

        // 5. redis 상품 적재 이벤트 리스너 발행
        publishInventoryInitializationEvent(creationOptionInventoryResult);
    }

    private void publishInventoryInitializationEvent(ProductOptionInventoryCreationResult creationOptionInventoryResult){
        List<InventoryItem> items = creationOptionInventoryResult.inventories()
                .stream()
                .map(inventory -> {
                    ProductOption option = inventory.getProductOption();

                    return new InventoryItem(
                            option.getProducts().getProductId(),
                            option.getProductOptionId(),
                            inventory.getTotalQuantity(),
                            inventory.getProductOption().getPrice(),
                            inventory.getProductOption().getCurrency(),
                            option.getUpdateVersion()
                    );
                })
                .toList();

        eventPublisher.publishEvent(new InventoryInitializedEvent(items));
    }

    private Products saveProduct(RequestProductDto requestProductDto, String userId){
        Products products = Products.create(
                requestProductDto.productCode(),
                requestProductDto.productName(),
                requestProductDto.description(),
                requestProductDto.brandName(),
                userId
        );
        return productsRepository.save(products);
    }

    private ProductOptionInventoryCreationResult saveProductOptionAndInventory(Products product, RequestProductDto requestProductDto){
        List<ProductOption> options = new ArrayList<>();
        List<ProductInventory> inventories = new ArrayList<>();

        for (RequestProductOptionDto optionDto : requestProductDto.productOptionDtos()) {
            ProductOption option = ProductOption.create(
                    product,
                    optionDto.optionName(),
                    optionDto.price(),
                    optionDto.currency()
            );

            options.add(option);
            inventories.add(
                    ProductInventory.create(
                            option,
                            optionDto.totalQuantity()
                    )
            );
        }

        productOptionRepository.saveAll(options);
        productInventoryRepository.saveAll(inventories);
        return new ProductOptionInventoryCreationResult(options, inventories);
    }

    private List<ProductNormalize> productNormalizeProcess(Products product, List<ProductOption> productOptions){
        List<ProductNormalize> result = new ArrayList<>();

        for (ProductOption option: productOptions){
            ProductNormalize productNormalize = ProductNormalize.builder()
                    .productId(product.getProductId())
                    .productOptionId(option.getProductOptionId())
                    .productName(product.getProductName())
                    .optionName(option.getOptionName())
                    .currency(option.getCurrency())
                    .price(option.getPrice())
                    .updateVersion(option.getUpdateVersion())
                    .build();
            result.add(productNormalize);
        }

        return result;
    }
}
