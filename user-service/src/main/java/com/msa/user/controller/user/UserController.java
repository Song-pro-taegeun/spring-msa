package com.msa.user.controller.user;

import com.msa.user.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    /**
     * 테넌트 provision 후 필터 및 인증인가, 스키마 동적 변경 가능한지 체크 용도의 api
     */
    @GetMapping("/me")
    public ResponseEntity<String> getMe(){
        return ResponseEntity.ok(userService.getMe());
    }
}
