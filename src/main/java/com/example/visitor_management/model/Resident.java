package com.example.visitor_management.model;
import jakarta.persistence.*;

@Entity

public class Resident {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, length = 15)
    private String phone;
    @ManyToOne(optional = false)
    private Flat flat;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Flat getFlat() { return flat; }
    public void setFlat(Flat flat) { this.flat = flat; }
}

