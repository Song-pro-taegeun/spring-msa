package com.msa.auth.entity.user;

import com.msa.common.entity.SecurityRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_tenant_key", columnNames = "tenant_key"),
                @UniqueConstraint(name = "uk_users_user_id", columnNames = "user_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_no")
    private Long userNo;

    @Column(name = "tenant_key", nullable = false, length = 36)
    private String tenantKey;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId; // 사용자 ID

    @Column(name = "user_pwd", nullable = false)
    private String userPwd; // 비밀번호

    @Column(name = "user_name", nullable = false)
    private String userName; // 사용자 이름

    @Column(name = "user_phone")
    private String userPhone; // 전화번호

    @Column(name = "user_addr")
    private String userAddr; // 주소

    @Column(name = "usere_addr_detail")
    private String userAddrDetail; // 상세 주소

    @CreationTimestamp
    @Column(name = "reg_dtm")
    private LocalDateTime regDtm; // 등록일자 (자동 생성)

    @Column(name = "user_role", nullable = false)
    private String userRole; // 시큐리티 유저 권한;
}
