package com.example.visitor_management.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record SmsSettings(
    @Value("${app.sms.enabled:false}") boolean enabled,
    @Value("${app.sms.account-sid:}") String accountSid,
    @Value("${app.sms.auth-token:}") String authToken,
    @Value("${app.sms.from-number:}") String fromNumber) {
    public boolean ready() {
        return enabled && accountSid.matches("AC[0-9a-fA-F]{32}") &&
            !authToken.isBlank() && fromNumber.matches("\\+[1-9][0-9]{7,14}");
    }
    // Never expose credentials through generated record logging.
    @Override public String toString() { return "SmsSettings[credentials hidden]"; }
}
