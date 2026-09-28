package com.example.visitor_management.repository;
import com.example.visitor_management.model.OTPPass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface OTPPassRepository extends JpaRepository<OTPPass, Long> {
    Optional<OTPPass> findByApprovalId(Long approvalId);
}
