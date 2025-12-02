package com.msa.auth.service;

import com.msa.auth.dto.AuthRequestDto;
import com.msa.auth.entity.Users;
import com.msa.auth.repository.UsersRepository;
import com.msa.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AuthService {
    private final UsersRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public void signUp(AuthRequestDto request){
        if (userRepository.findByUserId(request.getUserId()).isPresent()) {
            throw new DuplicateKeyException("아이디 중복");
        }

        Users user = Users.builder()
                .userId(request.getUserId())
                .userPwd(passwordEncoder.encode(request.getUserPwd()))
                .userName(request.getUserName())
                .userPhone(request.getUserPhone())
                .userAddr(request.getUserAddr())
                .userAddrDetail(request.getUserAddrDetail())
                .userRole("ROLE_USER") // 회원가입시 시큐리티 권한은 ROLE_USER
                .build();

        userRepository.save(user);
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
}
