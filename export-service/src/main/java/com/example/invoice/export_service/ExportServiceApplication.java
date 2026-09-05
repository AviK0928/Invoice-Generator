package com.example.invoice.export_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {
		"com.example.invoice.export_service",
		"com.example.invoice.common.inbox" })
@EnableJpaRepositories(basePackages = {
		"com.example.invoice.export_service",
		"com.example.invoice.common.inbox" })
public class ExportServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExportServiceApplication.class, args);
	}

}
