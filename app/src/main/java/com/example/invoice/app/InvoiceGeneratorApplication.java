package com.example.invoice.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The consolidated deployment — six Spring contexts collapsed into one.
 * See docs/adr/003 for why, and docs/DEPLOYMENT.md for the topology this
 * replaces.
 *
 * Not @SpringBootApplication. That annotation has no excludeFilters attribute,
 * and declaring @ComponentScan alongside it means two @ComponentScan
 * declarations on one class with conflicting attribute sources. This is its
 * exact expansion, written out so the scan can be filtered.
 *
 * Kafka auto-configuration is excluded, which is what makes the consumer
 * classes safe to keep as ordinary beans: @KafkaListener does nothing without
 * the annotation post-processor that @EnableKafka registers, so the listeners
 * are inert and their methods can be invoked directly by the in-process
 * publisher — filters, event-type switches and all — instead of being
 * reimplemented.
 *
 * FullyQualifiedAnnotationBeanNameGenerator is not cosmetic. Four class simple
 * names repeat across the modules — GlobalExceptionHandler in all five,
 * OpenApiConfig, KafkaTopicConfig, KafkaConsumerConfig — and the default
 * generator derives bean names from simple names, so scanning them together is
 * a ConflictingBeanDefinitionException. Fully-qualified names sidestep it
 * without renaming eighteen classes across five modules. @Bean method names are
 * unaffected, so containerFactory = "..." references still resolve.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
@EnableScheduling
@EntityScan("com.example.invoice")
@EnableJpaRepositories("com.example.invoice")
@ComponentScan(
        basePackages = "com.example.invoice",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        // Each carries its own @EnableJpaRepositories and
                        // @EntityScan. Two of those registrations in one
                        // context conflict; this class supplies one of each
                        // over the whole package instead.
                        com.example.invoice.customer_service.CustomerServiceApplication.class,
                        com.example.invoice.invoice_service.InvoiceServiceApplication.class,
                        com.example.invoice.export_service.ExportServiceApplication.class,
                        com.example.invoice.import_service.ImportServiceApplication.class,
                        com.example.invoice.archive_service.ArchiveServiceApplication.class,

                        // Broker plumbing. These live in common and were never
                        // scanned before — each service @Imports them
                        // explicitly, and a service only scans its own package.
                        // Scanning com.example.invoice picks them up, and the
                        // producer config fails on a missing
                        // spring.kafka.bootstrap-servers.
                        com.example.invoice.common.kafka.KafkaProducerConfiguration.class,
                        com.example.invoice.common.kafka.KafkaErrorHandlingConfiguration.class,

                        // NewTopic beans need a KafkaAdmin against a live
                        // broker.
                        com.example.invoice.customer_service.config.KafkaTopicConfig.class,
                        com.example.invoice.invoice_service.config.KafkaTopicConfig.class,
                        com.example.invoice.export_service.config.KafkaTopicConfig.class,
                        com.example.invoice.import_service.config.KafkaTopicConfig.class,
                        com.example.invoice.archive_service.config.KafkaTopicConfig.class,

                        // @EnableKafka plus consumer factories — the one thing
                        // that would start containers despite the auto-config
                        // exclusion.
                        com.example.invoice.invoice_service.config.KafkaConsumerConfig.class,
                        com.example.invoice.export_service.config.KafkaConsumerConfig.class,
                        com.example.invoice.archive_service.config.KafkaConsumerConfig.class,

                        // Five OpenAPI beans in one context is a
                        // NoUniqueBeanDefinitionException in springdoc. One
                        // replaces them here.
                        com.example.invoice.customer_service.config.OpenApiConfig.class,
                        com.example.invoice.invoice_service.config.OpenApiConfig.class,
                        com.example.invoice.export_service.config.OpenApiConfig.class,
                        com.example.invoice.import_service.config.OpenApiConfig.class,
                        com.example.invoice.archive_service.config.OpenApiConfig.class
                }))
public class InvoiceGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceGeneratorApplication.class, args);
    }
}