package com.example.invoice.export_service;

import com.example.invoice.common.kafka.KafkaErrorHandlingConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Consume-only: export-service builds a read model from invoice events and
 * publishes nothing, so it needs the inbox and the error handler but neither
 * the outbox nor a producer.
 *
 * @EnableScheduling is for the inbox cleanup job.
 */
@SpringBootApplication
@EnableScheduling
@Import(KafkaErrorHandlingConfiguration.class)
// @EntityScan REPLACES the default rather than adding to it — omit this
// service's own package and every entity silently disappears.
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