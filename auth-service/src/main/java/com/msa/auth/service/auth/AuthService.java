package com.msa.auth.service.auth;

import com.msa.auth.crypto.DbCredentialCrypto;
import com.msa.auth.crypto.EncryptionResult;
import com.msa.auth.dto.AuthRequestDto;
import com.msa.auth.entity.tenant.CredentialStatus;
import com.msa.auth.entity.tenant.TenantDbCredential;
import com.msa.auth.entity.user.Users;
import com.msa.auth.kafka.internal.UserCreatedInternalEvent;
import com.msa.auth.repository.tenant.TenantDbCredentialRepository;
import com.msa.auth.repository.user.UsersRepository;
import com.msa.auth.util.JwtUtil;
import com.msa.common.kafka_event.UserCreatedEvent;
import com.msa.auth.entity.tenant.Tenant;
import com.msa.auth.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AuthService {
    @Value("${service-name.user}")
    private String userServiceName;

    private final ApplicationEventPublisher eventPublisher;
    private final UsersRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantDbCredentialRepository tenantDbCredentialRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DbCredentialCrypto dbCredentialCrypto;

    @Transactional
    public void signUp(AuthRequestDto request){
        if (userRepository.findByUserId(request.getUserId()).isPresent()) {
            throw new DuplicateKeyException("아이디 중복");
        }

        String uuId = UUID.randomUUID().toString();
        String tenantKey = uuId.replace("-", "_"); // 스키마 생성 시 uuid로 생성하는데, "-" 해당 텍스트를 포함할 수 없음

        // 1. 테넌트 생성
        Tenant tenant = Tenant.builder()
                .tenantKey(tenantKey)
                .build();
        tenant.activate();
        Tenant savedTenant = tenantRepository.save(tenant);

        // 2. tenant 별 DB credential 생성
        createTenantDbCredential(savedTenant.getTenantKey(), userServiceName);

        // 3. User 생성
        Users user = Users.builder()
                .userId(request.getUserId())
                .tenantKey(tenant.getTenantKey()) // 테넌트 키 기입
                .userPwd(passwordEncoder.encode(request.getUserPwd()))
                .userName(request.getUserName())
                .userPhone(request.getUserPhone())
                .userAddr(request.getUserAddr())
                .userAddrDetail(request.getUserAddrDetail())
                .userRole("ROLE_USER") // 회원가입시 시큐리티 권한은 ROLE_USER
                .build();
        Users savedUser = userRepository.save(user);

        // 4. 이벤트 발행
        UserCreatedEvent event = UserCreatedEvent.builder()
                .tenantKey(savedTenant.getTenantKey())   // 있으면
                .userId(savedUser.getUserId())
                .userName(savedUser.getUserName())
                .regDtm(String.valueOf(savedUser.getRegDtm()))
                .build();

        /**
         * 트랜잭션 저장이 끝난 후 이벤트를 발행해야 하므로 내부 이벤트 발행 로직을 추가
         * Kafka를 직접 호출하지 않고, eventPublisher.publishEvent()로 Spring 내부 이벤트를 발행
         * publishEvent() 자체는 즉시 Kafka를 보내는 함수가 아니라, 이 트랜잭션이 커밋되면 그때 동작하는 hook임
         */
        eventPublisher.publishEvent(
                new UserCreatedInternalEvent(event)
        );

        /**
         * 테스트
         * 동기적으로 publishEvent 메써드 까지 호출(리스너를 실행하지 않음 이벤트를 등록)
         * Exception 강제 발생 시 UserCreatedEventListener 까지 전달 되지 않음(@TransactionalEventListener 관련 어노테이션으로 인해)
         * publishEvent()는 트랜잭션에 이벤트를 등록 하는 역할(해당 이벤트를 트랜잭션에 걸어둘 뿐)
         * 즉, 로직 도중 트랜잭션이 롤백 되면 리스너는 절대 실행되지 않음
         * publishEvent() -> 트랜잭션에 걸어두고 -> Spring 내부에서 현재 활성 트랜잭션이 있는지 확인
         *      -> 트랜잭션이 있다면, 이벤트를 TransactionSynchronizationManager에 등록, 없다면 fallback 옵션에 따라 즉시 실행 여부를 결정
         *
         */
        // throw new RuntimeException();


        /**
         * NextStep!!!!
         * Outbox Pattern은 도입하여 이벤트 발생 시 이벤트를 저장하는 테이블과 로직을 저장 필요
         * kafka 장애 시 대응 가능
         * 사용 케이스 : 결제 / 주문 / 회원생성 등 데이터 유실이 절대적으로 허용되면 안되는 경우
         * Outbox Pattern은 Producer 단위 이벤트 유실 방지 용도
         * Outbox Pattern 도입 예시 케이스
         *    user insert -> 트랜잭션 커밋 성공
         *    이후 서버 크래시 발생 (kafka send 호출 전/중)
         *      -> Auth DB엔 유저가 존재하지만 Kafka 이벤트는 유실될 수 있음
         *    이를 방지하기 위해, 비즈니스 데이터와 이벤트를 같은 트랜잭션으로 DB에 먼저 저장하고,
         *    서버 재시작 후에도 DB에 남아 있는 이벤트를 기반으로
         *    Kafka 발행을 재시도하기 위함
         */
    }

    public String login(String userId, String password){
        // 아이디 체크
        Users user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new AuthenticationServiceException("Invalid credentials"));

        // 패스워드 체크
        if (!passwordEncoder.matches(password, user.getUserPwd())) {
            throw new AuthenticationServiceException("Invalid credentials");
        }

        return jwtUtil.generateToken(user.getUserNo(), user.getUserId(), user.getUserRole());
    }

    private void createTenantDbCredential(
            String tenantId,
            String serviceName
    ) {
        // 1. DB 계정명 생성 규칙
        String username = serviceName + "_" + tenantId;

        // 2. 랜덤 패스워드 생성
        String rawPassword = UUID.randomUUID().toString().replace("-", "");

        // 3. AES 암호화
        EncryptionResult result = dbCredentialCrypto.encrypt(rawPassword);

        // 4. 엔티티 생성
        TenantDbCredential credential = TenantDbCredential.builder()
                .tenantId(tenantId)
                .serviceName(serviceName)
                .username(username)
                .passwordEnc(result.getEncrypted())
                .encIv(result.getIv())
                .status(CredentialStatus.ACTIVE)
                .build();

        tenantDbCredentialRepository.save(credential);
    }
}
