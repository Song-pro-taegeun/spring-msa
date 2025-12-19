package com.msa.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "USERS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_NO")
    private Long userNo;

    @Column(name = "TENANT_KEY", nullable = false, unique = true, length = 50)
    private String tenantKey;

    @Column(name = "USER_ID")
    private String userId; // 사용자 ID

    @Column(name = "USER_PWD")
    private String userPwd; // 비밀번호

    @Column(name = "USER_NAME")
    private String userName; // 사용자 이름

    @Column(name = "USER_PHONE")
    private String userPhone; // 전화번호

    @Column(name = "USER_ADDR")
    private String userAddr; // 주소

    @Column(name = "USER_ADDR_DETAIL")
    private String userAddrDetail; // 상세 주소

    @CreationTimestamp
    @Column(name = "REG_DTM")
    private LocalDateTime regDtm; // 등록일자 (자동 생성)

    @Column(name = "USER_ROLE", nullable = false)
    private String userRole; // 시큐리티 유저 권한;
}
