package com.msa.product.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "product-snapshot")
@Component
@Getter
public class TenantProductSnapshotProperties {
    private List<String> services = new ArrayList<>();
}
