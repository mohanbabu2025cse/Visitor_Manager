package com.example.visitor_management.service;
import com.example.visitor_management.dto.*;
import com.example.visitor_management.dto.Requests.*;
import com.example.visitor_management.exception.ApiException;
import com.example.visitor_management.model.*;
import com.example.visitor_management.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
public class VisitorService {
    private final FlatRepository flats;
    private final ResidentRepository residents;
    private final ApprovalRepository approvals;
    private final OTPPassRepository passes;
    private final SecureRandom random = new SecureRandom();

    public VisitorService(FlatRepository flats, ResidentRepository residents,
                          ApprovalRepository approvals, OTPPassRepository passes) {
        this.flats = flats; this.residents = residents; this.approvals = approvals; this.passes = passes;
    }
    public List<Flat> getFlats() { return flats.findAll(); }
    public Flat addFlat(FlatRequest request) {
        String number = request.number().trim().toUpperCase(java.util.Locale.ROOT);
        if (flats.existsByNumber(number)) throw new ApiException(CONFLICT, "Flat already exists.");
        Flat flat = new Flat(); flat.setNumber(number);
        flat.setAddress(request.address() == null ? "" : request.address().trim());
        return flats.save(flat);
    }
    public Flat updateFlat(Long id, FlatRequest request) {
        Flat flat = flats.findById(id).orElseThrow(() -> new ApiException(NOT_FOUND, "Flat not found."));
        String number = request.number().trim().toUpperCase(java.util.Locale.ROOT);
        if (!flat.getNumber().equals(number) && flats.existsByNumber(number))
            throw new ApiException(CONFLICT, "Flat already exists.");
        flat.setNumber(number);
        flat.setAddress(request.address() == null ? "" : request.address().trim());
        return flats.save(flat);
    }
    public Resident updateResident(Long id, ResidentRequest request) {
        Resident resident = resident(id);
        Flat flat = flats.findById(request.flatId()).orElseThrow(() -> new ApiException(NOT_FOUND, "Flat not found."));
        resident.setName(request.name().trim());
        resident.setPhone(request.phone().trim());
        resident.setFlat(flat);
        return residents.save(resident);
    }
    public List<Resident> getResidents() { return residents.findAll(); }
    public Resident addResident(ResidentRequest request) {
        Flat flat = flats.findById(request.flatId()).orElseThrow(() -> new ApiException(NOT_FOUND, "Flat not found."));
        Resident resident = new Resident();
        resident.setName(request.name().trim()); resident.setPhone(request.phone().trim()); resident.setFlat(flat);
        return residents.save(resident);
    }
    private Resident resident(Long id) {
        return residents.findById(id).orElseThrow(() -> new ApiException(NOT_FOUND, "Resident not found."));
    }
    public ApprovalView approve(Long residentId, ApprovalRequest request) {
        Resident resident = resident(residentId);
        LocalDateTime now = LocalDateTime.now();
        if (!request.expiresAt().isAfter(request.expectedAt()))
            throw new ApiException(BAD_REQUEST, "Expiry must be after the expected arrival.");
        if (!request.expiresAt().isAfter(now))
            throw new ApiException(BAD_REQUEST, "The visit window must not have already expired.");
        VisitorPreApproval a = new VisitorPreApproval();
        a.setResident(resident); a.setVisitorName(request.visitorName().trim());
        a.setVisitorPhone(request.visitorPhone().trim()); a.setPurpose(request.purpose().trim());
        a.setExpectedAt(request.expectedAt()); a.setExpiresAt(request.expiresAt());
        approvals.save(a);
        OTPPass pass = new OTPPass(); pass.setApproval(a);
        // Approval ID + OTP identify a pass; codes may repeat across different visits.
        pass.setCode(String.format("%06d", random.nextInt(1_000_000)));
        passes.save(pass);
        return ApprovalView.from(a, pass.getCode(), now);
    }
    public List<ApprovalView> list(Long residentId) {
        resident(residentId);
        LocalDateTime now = LocalDateTime.now();
        return approvals.findByResidentIdOrderByIdDesc(residentId).stream()
            .map(a -> ApprovalView.from(a, passes.findByApprovalId(a.getId()).orElseThrow().getCode(), now)).toList();
    }
    private VisitorPreApproval locked(Long id) {
        return approvals.findForUpdate(id).orElseThrow(() -> new ApiException(NOT_FOUND, "Approval not found."));
    }
    private void requirePending(VisitorPreApproval a, LocalDateTime now) {
        if (a.getStatus() != VisitStatus.PENDING)
            throw new ApiException(CONFLICT, "This visit is " + a.getStatus().name().toLowerCase() + ".");
        if (!now.isBefore(a.getExpiresAt())) throw new ApiException(CONFLICT, "This visit has expired.");
    }
    public ApprovalView revoke(Long residentId, Long approvalId) {
        VisitorPreApproval a = locked(approvalId);
        if (!a.getResident().getId().equals(residentId))
            throw new ApiException(NOT_FOUND, "Approval not found for this resident.");
        LocalDateTime now = LocalDateTime.now();
        requirePending(a, now);
        a.setStatus(VisitStatus.REVOKED);
        return ApprovalView.from(a, null, now);
    }
    public ApprovalView enter(EntryRequest request) {
        // The database row lock prevents two guards from consuming the same pass concurrently.
        VisitorPreApproval a = locked(request.approvalId());
        LocalDateTime now = LocalDateTime.now();
        requirePending(a, now);
        if (now.isBefore(a.getExpectedAt())) throw new ApiException(CONFLICT, "The visit window has not started.");
        OTPPass pass = passes.findByApprovalId(a.getId()).orElseThrow();
        if (!pass.getCode().equals(request.otp())) throw new ApiException(BAD_REQUEST, "Incorrect OTP.");
        if (pass.isUsed()) throw new ApiException(CONFLICT, "OTP has already been used.");
        pass.setUsed(true); a.setStatus(VisitStatus.ENTERED); a.setEntryTime(now);
        return ApprovalView.from(a, null, now);
    }
    @Scheduled(fixedDelay = 60000)
    public void expireUnused() {
        approvals.expirePending(LocalDateTime.now(), VisitStatus.PENDING, VisitStatus.EXPIRED);
    }
}
