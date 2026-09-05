package com.example.invoice.archive_service;

import com.example.invoice.common.outbox.OutboxConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling is for the outbox dispatcher only. The retention scheduler
 *                   this annotation once enabled was deleted — it exported to
 *                   an ephemeral
 *                   directory and then deleted the rows. See docs/adr/002.
 */
@SpringBootApplication
@EnableScheduling
@Import(OutboxConfiguration.class)
// @EntityScan REPLACES the default rather than adding to it — omit this
// service's own package and every entity silently disappears.
@EntityScan(basePackages = {
		"com.example.invoice.archive_service",
		"com.example.invoice.common.outbox" })
@EnableJpaRepositories(basePackages = {
		"com.example.invoice.archive_service",
		"com.example.invoice.common.outbox" })
public class ArchiveServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArchiveServiceApplication.class, args);
	}
}