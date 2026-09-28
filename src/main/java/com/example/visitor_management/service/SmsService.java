package com.example.visitor_management.service;

import com.example.visitor_management.dto.SmsResult;
import com.example.visitor_management.exception.ApiException;
import com.example.visitor_management.model.VisitStatus;
import com.example.visitor_management.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import static org.springframework.http.HttpStatus.*;

@Service
public class SmsService {
    private final SmsSettings settings;
    private final TwilioSmsClient client;
    private final ApprovalRepository approvals;
    private final OTPPassRepository passes;

    public SmsService(SmsSettings settings, TwilioSmsClient client,
                      ApprovalRepository approvals, OTPPassRepository passes) {
        this.settings = settings; this.client = client; this.approvals = approvals; this.passes = passes;
    }
    public boolean available() { return settings.ready(); }

    public static String normalizePhone(String value) {
        String phone = value.replace(" ", "").replace("-", "");
        // This project defaults to India for a local ten-digit mobile number.
        if (phone.matches("[6-9][0-9]{9}")) phone = "+91" + phone;
        if (!phone.matches("\\+[1-9][0-9]{7,14}"))
            throw new ApiException(BAD_REQUEST, "Enter a 10-digit Indian mobile number or an international number with +country code.");
        return phone;
    }

    @Transactional
    public SmsResult send(Long residentId, Long approvalId) {
        if (!settings.ready()) throw new ApiException(SERVICE_UNAVAILABLE,
            "SMS is not configured. Add the SMS provider settings on the server and restart. You can still copy the pass.");
        // Lock the existing, committed approval to prevent concurrent sends or revocation during submission.
        var approval = approvals.findForUpdate(approvalId)
            .orElseThrow(() -> new ApiException(NOT_FOUND, "Approval not found."));
        if (!approval.getResident().getId().equals(residentId))
            throw new ApiException(NOT_FOUND, "Approval not found for this resident.");
        LocalDateTime now = LocalDateTime.now();
        if (approval.getStatus() != VisitStatus.PENDING || !now.isBefore(approval.getExpiresAt()))
            throw new ApiException(CONFLICT, "SMS can only be sent for a pending, unexpired visit.");
        var pass = passes.findByApprovalId(approvalId).orElseThrow();
        if (pass.isUsed()) throw new ApiException(CONFLICT, "This pass has already been used.");
        if (pass.getSmsAttemptedAt() != null && now.isBefore(pass.getSmsAttemptedAt().plusSeconds(60)))
            throw new ApiException(TOO_MANY_REQUESTS, "Please wait 60 seconds between SMS attempts for this pass.");
        String phone = normalizePhone(approval.getVisitorPhone());
        String body = "Arrival visitor pass: ID " + approvalId + ", OTP " + pass.getCode() +
            ". Valid " + approval.getExpectedAt().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")) +
            " to " + approval.getExpiresAt().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")) +
            " (community local time). Single entry only.";
        pass.setSmsAttemptedAt(now);
        SmsResult result = client.send(phone, body);
        pass.setSmsStatus(result.status());
        return result;
    }
}
