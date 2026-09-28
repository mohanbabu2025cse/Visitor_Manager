package com.example.visitor_management.repository;
import com.example.visitor_management.model.Resident;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResidentRepository extends JpaRepository<Resident, Long> {}
