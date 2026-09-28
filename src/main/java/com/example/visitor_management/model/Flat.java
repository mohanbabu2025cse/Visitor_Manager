package com.example.visitor_management.model;
import jakarta.persistence.*;

@Entity

public class Flat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String number;
    @Column(length = 200)
    private String address;

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
}

