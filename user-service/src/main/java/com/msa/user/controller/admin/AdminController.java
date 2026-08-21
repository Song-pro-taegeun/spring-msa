package com.msa.user.controller.admin;

import com.msa.common.dto.CommonRequestProvisionReplayDlqDto;
import com.msa.user.service.admin.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminService adminService;

    @PostMapping("/dlq-events/replay/provision")
    public ResponseEntity<String> replayProvisionDlq(
            @RequestBody CommonRequestProvisionReplayDlqDto req
    ){
        String result = adminService.replayProvisionDlq(req);
        return ResponseEntity.ok(result);
    }

}
