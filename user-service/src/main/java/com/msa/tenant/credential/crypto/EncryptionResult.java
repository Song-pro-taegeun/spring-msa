package com.msa.tenant.credential.crypto;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EncryptionResult {
    private final byte[] encrypted;
    private final byte[] iv;
}