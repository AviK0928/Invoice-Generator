package com.example.invoice.export_service.controller;

import com.example.invoice.export_service.IntegrationTest;
import com.example.invoice.export_service.entity.PdfDocument;
import com.example.invoice.export_service.repository.PdfDocumentRepository;
import com.example.invoice.export_service.service.PdfDocumentCleanupService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * One-shot download. The row is deleted as the bytes are returned, which is the
 * whole storage strategy: a PDF exists only between generation and the user
 * fetching it, so nothing accumulates and there is no retention policy to
 * write.
 */
class PdfDownloadControllerIT extends IntegrationTest {

        private static final UUID REQUEST_ID = UUID.randomUUID();

        @Autowired
        MockMvc mockMvc;
        @Autowired
        PdfDocumentRepository pdfDocumentRepository;
        @Autowired
        PdfDocumentCleanupService cleanupService;

        @BeforeEach
        void seed() {
                pdfDocumentRepository.deleteAll();
                pdfDocumentRepository.save(PdfDocument.builder()
                                .requestId(REQUEST_ID)
                                .invoiceId(1001L)
                                .content("%PDF-1.4 fake".getBytes(StandardCharsets.ISO_8859_1))
                                .createdAt(LocalDateTime.now())
                                .build());
        }

        @Test
        @DisplayName("the PDF downloads once and is then gone")
        void downloadsOnceThenDeletes() throws Exception {
                mockMvc.perform(get("/api/exports/pdf/" + REQUEST_ID))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"invoice_1001.pdf\""));

                assertThat(pdfDocumentRepository.count()).isZero();

                // A second attempt is a 404, not an empty file. The client is told to
                // request a new one rather than being handed nothing.
                mockMvc.perform(get("/api/exports/pdf/" + REQUEST_ID))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.title").value("PDF not available"));
        }

        @Test
        @DisplayName("an unknown request is a problem+json 404")
        void unknownRequestIsNotFound() throws Exception {
                mockMvc.perform(get("/api/exports/pdf/" + UUID.randomUUID()))
                                .andExpect(status().isNotFound())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                                .andExpect(jsonPath("$.type")
                                                .value("https://invoice-generator/errors/pdf-not-available"));
        }

        @Test
        @DisplayName("undownloaded PDFs older than the retention window are deleted")
        void expiredDocumentsAreCleanedUp() {
                pdfDocumentRepository.save(PdfDocument.builder()
                                .requestId(UUID.randomUUID())
                                .invoiceId(1002L)
                                .content("%PDF-1.4 old".getBytes(StandardCharsets.ISO_8859_1))
                                .createdAt(LocalDateTime.now().minusHours(48))
                                .build());

                assertThat(cleanupService.deleteExpired()).isOne();

                // The one seeded in @BeforeEach is recent and survives.
                assertThat(pdfDocumentRepository.count()).isOne();
        }
}