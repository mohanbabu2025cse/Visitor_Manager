package com.example.visitor_management.controller;
import com.example.visitor_management.dto.*;
import com.example.visitor_management.dto.Requests.*;
import com.example.visitor_management.model.*;
import com.example.visitor_management.service.VisitorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class VisitorController {
    private final VisitorService service;
    public VisitorController(VisitorService service) { this.service = service; }
    @GetMapping("/flats")
    public List<Flat> flats() { return service.getFlats(); }
    @PostMapping("/flats") @ResponseStatus(HttpStatus.CREATED)
    public Flat addFlat(@Valid @RequestBody FlatRequest request) { return service.addFlat(request); }
    @PutMapping("/flats/{id}")
    public Flat updateFlat(@PathVariable @Positive Long id, @Valid @RequestBody FlatRequest request) {
        return service.updateFlat(id, request);
    }
    @PutMapping("/residents/{id}")
    public Resident updateResident(@PathVariable @Positive Long id, @Valid @RequestBody ResidentRequest request) {
        return service.updateResident(id, request);
    }
    @GetMapping("/residents")
    public List<Resident> residents() { return service.getResidents(); }
    @PostMapping("/residents") @ResponseStatus(HttpStatus.CREATED)
    public Resident addResident(@Valid @RequestBody ResidentRequest request) { return service.addResident(request); }
    @GetMapping("/residents/{residentId}/approvals")
    public List<ApprovalView> approvals(@PathVariable @Positive Long residentId) { return service.list(residentId); }
    @PostMapping("/residents/{residentId}/approvals") @ResponseStatus(HttpStatus.CREATED)
    public ApprovalView approve(@PathVariable @Positive Long residentId, @Valid @RequestBody ApprovalRequest request) {
        return service.approve(residentId, request);
    }
    @PutMapping("/residents/{residentId}/approvals/{approvalId}/revoke")
    public ApprovalView revoke(@PathVariable @Positive Long residentId, @PathVariable @Positive Long approvalId) {
        return service.revoke(residentId, approvalId);
    }
    @PostMapping("/security/entries")
    public ApprovalView enter(@Valid @RequestBody EntryRequest request) { return service.enter(request); }
}
