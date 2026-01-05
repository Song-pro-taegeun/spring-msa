package com.msa.user.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class DbCredentialCrypto {
    private static final String ALGORITHM = "AES/GCM/NoPadding"; // 현업 표준 암호화 방식(기밀성 + 무결성을 제공)
    private static final int TAG_LENGTH_BIT = 128; // 16 bytes 인증 태그
    private static final int IV_LENGTH_BYTE = 12;  // GCM 권장 길이

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom(); // IV 생성용

    /**
     * 평문 문자열을 AES-GCM으로 암호화
     */
    public EncryptionResult encrypt(String plainText) {
        try {
            // 암호화 마다 새로운 IV 생성
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            // Cipher 초기화
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv)
            );

            // UTF-8로 인코딩 후 암호화 로직 실행
            byte[] encrypted = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            return new EncryptionResult(encrypted, iv);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("DB credential encryption failed", e);
        }
    }

    /**
     * AES-GCM 암호문 복호화
     */
    public String decrypt(byte[] encrypted, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv)
            );

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("DB credential decryption failed", e);
        }
    }
}
