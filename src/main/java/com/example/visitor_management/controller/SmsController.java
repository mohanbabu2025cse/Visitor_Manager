package com.example.visitor_management.controller;

import com.example.visitor_management.dto.SmsResult;
import com.example.visitor_management.service.SmsService;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SmsController {
    private final SmsService sms;
    public SmsController(SmsService sms) { this.sms = sms; }

    @GetMapping("/sms/status")
    public Map<String, Boolean> status() { return Map.of("enabled", sms.available()); }

    @PostMapping("/residents/{residentId}/approvals/{approvalId}/sms")
    public SmsResult send(@PathVariable @Positive Long residentId, @PathVariable @Positive Long approvalId) {
        return sms.send(residentId, approvalId);
    }
}
