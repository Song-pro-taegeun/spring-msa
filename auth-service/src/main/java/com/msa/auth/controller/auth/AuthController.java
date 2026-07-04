package com.msa.auth.controller.auth;

import com.msa.auth.dto.AuthRequestDto;
import com.msa.auth.dto.AuthRequestLoginDto;
import com.msa.auth.dto.AuthResponseDto;
import com.msa.auth.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/signUp")
    public ResponseEntity<?> signUp(@RequestBody AuthRequestDto authRequestDto){
        authService.signUp(authRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(
            @RequestBody AuthRequestLoginDto authRequestLoginDto
            ) {
        return ResponseEntity.ok(
                new AuthResponseDto(authService.login(authRequestLoginDto))
        );
    }
}
