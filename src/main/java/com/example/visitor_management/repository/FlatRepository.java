package com.example.visitor_management.repository;
import com.example.visitor_management.model.Flat;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FlatRepository extends JpaRepository<Flat, Long> {
    boolean existsByNumber(String number);
}
