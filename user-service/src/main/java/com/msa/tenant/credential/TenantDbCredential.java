package com.msa.tenant.credential;

import com.msa.common.credential.CredentialStatus;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class TenantDbCredential {
    private Long tenantDbCrdId;
    private String tenantKey;
    private String serviceName;
    private String username;
    private byte[] passwordEnc;
    private byte[] encIv;
    private CredentialStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
