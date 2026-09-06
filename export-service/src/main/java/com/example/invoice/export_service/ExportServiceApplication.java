package com.example.invoice.export_service;

import com.example.invoice.common.kafka.KafkaErrorHandlingConfiguration;
import com.example.invoice.common.kafka.KafkaProducerConfiguration;
import com.example.invoice.common.outbox.OutboxConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Consumes invoice events into a read model, and — since the asynchronous PDF
 * feature — publishes too: a PDF request is answered with a ready event, which
 * must commit with the generated document rather than be sent alongside it.
 * So it needs the inbox, the outbox, a producer and the error handler.
 *
 * @EnableScheduling drives the inbox cleanup job and the outbox dispatcher.
 */
@SpringBootApplication
@EnableScheduling
@Import({ OutboxConfiguration.class,
		KafkaProducerConfiguration.class,
		KafkaErrorHandlingConfiguration.class })
// @EntityScan REPLACES the default rather than adding to it — omit this
// service's own package and every entity silently disappears.
@EntityScan(basePackages = {
		"com.example.invoice.export_service",
		"com.example.invoice.common.inbox",
		"com.example.invoice.common.outbox" })
@EnableJpaRepositories(basePackages = {
		"com.example.invoice.export_service",
		"com.example.invoice.common.inbox",
		"com.example.invoice.common.outbox" })
public class ExportServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExportServiceApplication.class, args);
	}
}