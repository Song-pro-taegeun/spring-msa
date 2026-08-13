package com.msa.order.config;

import com.msa.common.credential.crypto.DbCredentialCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class CryptoConfig {

    @Bean
    public SecretKey dbCredentialSecretKey(
            @Value("${crypto.master-key}") String base64Key
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(keyBytes, "AES");
    }

    @Bean
    public DbCredentialCrypto dbCredentialCrypto(SecretKey secretKey) {
        return new DbCredentialCrypto(secretKey);
    }
}

