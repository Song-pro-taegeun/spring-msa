package com.msa.user.controller.admin;

import com.msa.user.entity.user.Users;
import com.msa.user.service.admin.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/test")
    public ResponseEntity<List<Users>> getUsers(){
        return ResponseEntity.ok(adminService.getUsers());
    }

}
