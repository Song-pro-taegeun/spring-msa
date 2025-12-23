package com.msa.user.controller;

import com.msa.user.entity.UserTest;
import com.msa.user.service.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/test")
public class TestController {
    private final TestService testService;

    @GetMapping()
    public ResponseEntity<List<UserTest>> getTestUsers() {
        List<UserTest> result = testService.getTestUsers();
        return ResponseEntity.ok(result);
    }
}
