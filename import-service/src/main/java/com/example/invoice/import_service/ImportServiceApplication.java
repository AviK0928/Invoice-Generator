package com.example.invoice.import_service;

import com.example.invoice.common.kafka.KafkaProducerConfiguration;
import com.example.invoice.common.outbox.OutboxConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Required, or @Scheduled on the dispatcher is silently ignored and events
// queue forever with no error.
@EnableScheduling
@Import({ OutboxConfiguration.class, KafkaProducerConfiguration.class })
// @EntityScan REPLACES the default rather than adding to it — omit this
// service's own package and every entity silently disappears.
@EntityScan(basePackages = {
		"com.example.invoice.import_service",
		"com.example.invoice.common.outbox" })
@EnableJpaRepositories(basePackages = {
		"com.example.invoice.import_service",
		"com.example.invoice.common.outbox" })
public class ImportServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ImportServiceApplication.class, args);
	}
}