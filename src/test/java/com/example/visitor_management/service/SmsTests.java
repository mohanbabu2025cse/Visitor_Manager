package com.example.visitor_management.service;
import com.example.visitor_management.dto.SmsResult;
import com.example.visitor_management.exception.ApiException;
import com.example.visitor_management.model.*;
import com.example.visitor_management.repository.*;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class SmsTests {
    private final SmsSettings settings = new SmsSettings(true,
        "AC00000000000000000000000000000000", "test-token", "+15005550006");
    @Test void normalizesNumbersAndRejectsInvalidInput() {
        assertEquals("+919876543210", SmsService.normalizePhone("98765-43210"));
        assertEquals("+441234567890", SmsService.normalizePhone("+44 1234567890"));
        assertThrows(ApiException.class, () -> SmsService.normalizePhone("123"));
        assertThrows(ApiException.class, () -> SmsService.normalizePhone("++919876543210"));
    }
    @Test void configurationMustBeCompleteAndDoesNotExposeSecrets() {
        assertTrue(settings.ready());
        assertFalse(new SmsSettings(false, settings.accountSid(), "token", "+15005550006").ready());
        assertFalse(new SmsSettings(true, "", "", "").ready());
        assertFalse(settings.toString().contains("test-token"));
    }
    @Test void submittedDoesNotClaimPhoneDelivery() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(201);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        var result = new TwilioSmsClient(settings, http).send("+919876543210", "OTP 012345");
        assertEquals("SUBMITTED", result.status());
        assertTrue(result.message().contains("not yet confirmed"));
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("api.twilio.com", request.getValue().uri().getHost());
        assertEquals("POST", request.getValue().method());
        assertEquals(java.time.Duration.ofSeconds(10), request.getValue().timeout().orElseThrow());
    }
    @Test void rejectedRequestReturnsFailure() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        assertEquals("FAILED", new TwilioSmsClient(settings, http).send("+919876543210", "OTP 012345").status());
    }
    @Test void connectionFailureIsUnknownNotSent() throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenThrow(new IOException("simulated timeout"));
        assertEquals("UNKNOWN", new TwilioSmsClient(settings, http).send("+919876543210", "OTP 012345").status());
    }
    private final ApprovalRepository approvals = mock(ApprovalRepository.class);
    private final OTPPassRepository passes = mock(OTPPassRepository.class);
    private final TwilioSmsClient client = mock(TwilioSmsClient.class);
    private VisitorPreApproval pending() {
        Resident resident = new Resident(); resident.setId(1L);
        VisitorPreApproval a = new VisitorPreApproval(); a.setId(2L); a.setResident(resident);
        a.setVisitorPhone("9876543210"); a.setExpectedAt(LocalDateTime.now());
        a.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(approvals.findForUpdate(2L)).thenReturn(Optional.of(a));
        return a;
    }
    @Test void unavailableSmsDoesNotContactProvider() {
        SmsService service = new SmsService(new SmsSettings(false, "", "", ""), client, approvals, passes);
        assertThrows(ApiException.class, () -> service.send(1L, 2L));
        verifyNoInteractions(client, approvals);
    }
    @Test void sendsExistingCodeAndLimitsRepeatedAttemptsEvenAfterFailure() {
        pending();
        OTPPass pass = new OTPPass(); pass.setCode("012345");
        when(passes.findByApprovalId(2L)).thenReturn(Optional.of(pass));
        when(client.send(eq("+919876543210"), anyString())).thenReturn(new SmsResult("FAILED", "Rejected"));
        SmsService service = new SmsService(settings, client, approvals, passes);
        assertEquals("FAILED", service.send(1L, 2L).status());
        assertEquals("FAILED", pass.getSmsStatus());
        assertNotNull(pass.getSmsAttemptedAt());
        assertThrows(ApiException.class, () -> service.send(1L, 2L));
        verify(client, times(1)).send(eq("+919876543210"), contains("ID 2, OTP 012345"));
        assertFalse(pass.isUsed());
    }
    @Test void rejectsWrongResidentAndUnavailablePassStates() {
        var a = pending();
        SmsService service = new SmsService(settings, client, approvals, passes);
        assertThrows(ApiException.class, () -> service.send(99L, 2L));
        for (VisitStatus state : new VisitStatus[]{VisitStatus.ENTERED, VisitStatus.REVOKED, VisitStatus.EXPIRED}) {
            a.setStatus(state);
            assertThrows(ApiException.class, () -> service.send(1L, 2L));
        }
        a.setStatus(VisitStatus.PENDING); a.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(ApiException.class, () -> service.send(1L, 2L));
        verifyNoInteractions(client);
    }
}
