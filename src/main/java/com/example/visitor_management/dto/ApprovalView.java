package com.example.visitor_management.dto;
import com.example.visitor_management.model.*;
import java.time.LocalDateTime;

public record ApprovalView(Long id, Long residentId, String visitorName, String visitorPhone,
        String purpose, LocalDateTime expectedAt, LocalDateTime expiresAt,
        VisitStatus status, LocalDateTime entryTime, String otp) {
    public static ApprovalView from(VisitorPreApproval a, String otp, LocalDateTime now) {
        VisitStatus status = a.getStatus();
        if (status == VisitStatus.PENDING && !now.isBefore(a.getExpiresAt())) status = VisitStatus.EXPIRED;
        return new ApprovalView(a.getId(), a.getResident().getId(), a.getVisitorName(),
            a.getVisitorPhone(), a.getPurpose(), a.getExpectedAt(), a.getExpiresAt(),
            status, a.getEntryTime(), status == VisitStatus.PENDING ? otp : null);
    }
}
