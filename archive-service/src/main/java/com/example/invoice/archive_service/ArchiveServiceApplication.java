package com.example.invoice.archive_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArchiveServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ArchiveServiceApplication.class, args);
	}
}