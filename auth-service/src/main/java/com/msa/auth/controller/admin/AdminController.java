package com.msa.auth.controller.admin;

import com.msa.auth.service.outbox.OutboxRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final OutboxRetryService outboxRetryService;

    @PostMapping("/outbox-events/retry/{eventId}")
    public ResponseEntity<Void> retry(@PathVariable String eventId) {
        outboxRetryService.retry(eventId);
        return ResponseEntity.ok().build();
    }
}
