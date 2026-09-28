package com.example.visitor_management.model;
import jakarta.persistence.*;

@Entity

public class OTPPass {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false)
    @JoinColumn(nullable = false, unique = true)
    private VisitorPreApproval approval;
    @Column(nullable = false, length = 6)
    private String code;
    private boolean used;
    private java.time.LocalDateTime smsAttemptedAt;
    private String smsStatus;

    public java.time.LocalDateTime getSmsAttemptedAt() { return smsAttemptedAt; }
    public void setSmsAttemptedAt(java.time.LocalDateTime value) { smsAttemptedAt = value; }
    public String getSmsStatus() { return smsStatus; }
    public void setSmsStatus(String value) { smsStatus = value; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public VisitorPreApproval getApproval() { return approval; }
    public void setApproval(VisitorPreApproval approval) { this.approval = approval; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
}

