package com.msa.product.service.redis;

import com.msa.product.event.internal.InventoryItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class InventoryRedisService {
    public void initializeInventories(List<InventoryItem> items){

    }
}
