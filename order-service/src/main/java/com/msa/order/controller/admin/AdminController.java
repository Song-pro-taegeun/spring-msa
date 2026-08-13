package com.msa.order.controller.admin;

import com.msa.common.dto.CommonRequestProvisionReplayDlqDto;
import com.msa.order.service.admin.AdminService;
import com.msa.order.service.outbox.OutboxRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminService adminService;
    private final OutboxRetryService outboxRetryService;

    @PostMapping("/outbox-events/retry/{eventId}")
    public ResponseEntity<Void> retry(@PathVariable String eventId) {
        outboxRetryService.retry(eventId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/replay/provision")
    public ResponseEntity<String> replayProvisionDlq(
            @RequestBody CommonRequestProvisionReplayDlqDto req
    ){
        String result = adminService.replayProvisionDlq(req);
        return ResponseEntity.ok(result);
    }

}
