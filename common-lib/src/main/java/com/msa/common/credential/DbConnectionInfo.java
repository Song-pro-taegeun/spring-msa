package com.msa.common.credential;

/**
 * 멀티테넌트 DB 커넥션 생성에 필요한 최소 정보 DTO
 * - JPA Entity x
 * - 암호화 정보 x
 * - Hibernate 의존 x
 */
public record DbConnectionInfo(
        String schema,   // 접속할 DB(schema) 이름
        String username, // DB 계정
        String password  // 복호화된 평문 패스워드
) {
}
