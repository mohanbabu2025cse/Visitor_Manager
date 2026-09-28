package com.example.visitor_management;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class VisitorManagementApplication {
	public static void main(String[] args) {
		SpringApplication.run(VisitorManagementApplication.class, args);
	}

}