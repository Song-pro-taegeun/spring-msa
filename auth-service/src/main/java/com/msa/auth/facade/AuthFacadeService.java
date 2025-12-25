package com.msa.auth.facade;

import com.msa.auth.entity.Users;
import com.msa.auth.service.auth.AuthService;
import com.msa.auth.service.user.UserService;
import com.msa.tenant.config.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 서비스 메써드 호출 단계에서 DB 커넥션을 바꿀 때, 파사드패턴을 통해 테넌트 컨텍스트를 바꾼다.
 * MSA 구조에선 하나의 서비스에서 커넥션을 다르게 호출하는 일은 발생하면 안된다.
 * Kafka를 통해 EDA(event driven architecture) 방식으로 각 서비스 DB에 값을 적재해야한다.
 * 해당 파사드 패턴은 학습용도로 기록.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFacadeService {
    private final AuthService authService;
    private final UserService userService;

    public void getUsers(){
        try{
            TenantContext.set("msa_auth");
            List<Users> authUsers = authService.getAuthSchemaUsers();
            log.info(authUsers.toString());

            TenantContext.set("msa_user");
            List<Users> userUsers = userService.getUserSchemaUsers();
            log.info(userUsers.toString());
        }finally {
            TenantContext.clear();
        }
    }
}
