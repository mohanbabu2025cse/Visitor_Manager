package com.example.visitor_management.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

// Records hold incoming JSON without exposing database entities for changes.
public class Requests {
    public record FlatRequest(@NotBlank @Size(max=30) String number, @Size(max=200) String address) {
        public FlatRequest(String number) { this(number, null); }
    }
    public record ResidentRequest(@NotBlank @Size(max=80) String name,
        @NotBlank @Pattern(regexp="[0-9+ -]{7,15}", message="must contain 7 to 15 phone characters") String phone,
        @NotNull @Positive Long flatId) {}
    public record ApprovalRequest(@NotBlank @Size(max=80) String visitorName,
        @NotBlank @Pattern(regexp="[0-9+ -]{7,20}", message="must contain 7 to 20 phone characters") String visitorPhone,
        @NotBlank @Size(max=200) String purpose,
        @NotNull LocalDateTime expectedAt, @NotNull LocalDateTime expiresAt) {}
    public record EntryRequest(@NotNull @Positive Long approvalId,
        @NotBlank @Pattern(regexp="[0-9]{6}", message="must be exactly six digits") String otp) {}
}
