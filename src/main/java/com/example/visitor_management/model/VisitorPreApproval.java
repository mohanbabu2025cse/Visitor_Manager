package com.example.visitor_management.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity

public class VisitorPreApproval {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Resident resident;
    @Column(nullable = false, length = 80)
    private String visitorName;
    @Column(nullable = false, length = 20)
    private String visitorPhone;
    @Column(nullable = false, length = 200)
    private String purpose;
    @Column(nullable = false)
    private LocalDateTime expectedAt;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    private LocalDateTime entryTime;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VisitStatus status = VisitStatus.PENDING;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Resident getResident() { return resident; }
    public void setResident(Resident resident) { this.resident = resident; }

    public String getVisitorName() { return visitorName; }
    public void setVisitorName(String visitorName) { this.visitorName = visitorName; }

    public String getVisitorPhone() { return visitorPhone; }
    public void setVisitorPhone(String visitorPhone) { this.visitorPhone = visitorPhone; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public LocalDateTime getExpectedAt() { return expectedAt; }
    public void setExpectedAt(LocalDateTime expectedAt) { this.expectedAt = expectedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getEntryTime() { return entryTime; }
    public void setEntryTime(LocalDateTime entryTime) { this.entryTime = entryTime; }

    public VisitStatus getStatus() { return status; }
    public void setStatus(VisitStatus status) { this.status = status; }
}

