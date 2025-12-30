package com.msa.auth.crypto;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EncryptionResult {
    private final byte[] encrypted;
    private final byte[] iv;
}