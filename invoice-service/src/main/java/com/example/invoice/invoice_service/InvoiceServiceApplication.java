package com.example.invoice.invoice_service;

import com.example.invoice.common.kafka.KafkaErrorHandlingConfiguration;
import com.example.invoice.common.kafka.KafkaProducerConfiguration;
import com.example.invoice.common.outbox.OutboxConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({ OutboxConfiguration.class,
		KafkaProducerConfiguration.class,
		KafkaErrorHandlingConfiguration.class })
// @EntityScan REPLACES the default rather than adding to it — omit this
// service's own package and every entity silently disappears.
@EntityScan(basePackages = {
		"com.example.invoice.invoice_service",
		"com.example.invoice.common.outbox" })
@EnableJpaRepositories(basePackages = {
		"com.example.invoice.invoice_service",
		"com.example.invoice.common.outbox" })
public class InvoiceServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(InvoiceServiceApplication.class, args);
	}
}