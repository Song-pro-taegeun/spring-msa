package com.msa.auth.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "tenant.provision")
@Component
@Getter
public class TenantProvisionProperties {
    private List<String> services = new ArrayList<>();
}
