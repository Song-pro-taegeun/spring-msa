package com.msa.auth.dto;

public record AuthRequestLoginDto (
        String userId,
        String password
){
}
