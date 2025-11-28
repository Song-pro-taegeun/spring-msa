package com.msa.auth.dto;

import lombok.Data;

@Data
public class AuthRequestDto {
    private String userId; // 사용자 ID
    private String userPwd; // 비밀번호

    private String userName; // 사용자 이름
    private String userPhone; // 전화번호
    private String userAddr; // 주소
    private String userAddrDetail; // 상세 주소
}
