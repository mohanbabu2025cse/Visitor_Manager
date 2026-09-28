package com.example.visitor_management.repository;
import com.example.visitor_management.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface ApprovalRepository extends JpaRepository<VisitorPreApproval, Long> {
    List<VisitorPreApproval> findByResidentIdOrderByIdDesc(Long residentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from VisitorPreApproval a where a.id = :id")
    Optional<VisitorPreApproval> findForUpdate(@Param("id") Long id);
    @Modifying
    @Query("update VisitorPreApproval a set a.status = :expired where a.status = :pending and a.expiresAt <= :now")
    int expirePending(@Param("now") LocalDateTime now, @Param("pending") VisitStatus pending,
                      @Param("expired") VisitStatus expired);
}
