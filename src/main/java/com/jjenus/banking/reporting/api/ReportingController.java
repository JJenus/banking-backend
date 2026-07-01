package com.jjenus.banking.reporting.api;

import com.jjenus.banking.reporting.application.ReportingApplicationService;
import com.jjenus.banking.reporting.application.StatementPdfGenerator;
import com.jjenus.banking.reporting.domain.AccountStatement;
import com.jjenus.banking.reporting.domain.TrialBalance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * REST controller for the {@code reporting} module.
 *
 * <p>Base path: {@code /api/v1/reporting}
 *
 * <p>All endpoints are read-only. No data is modified by this module.
 */
@RestController
@RequestMapping("/v1/reporting")
@Tag(name = "Reporting", description = "Account statements, trial balance, PDF export")
@SecurityRequirement(name = "bearer-key")
public class ReportingController {

    private static final DateTimeFormatter FILE_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final ReportingApplicationService reportingService;
    private final StatementPdfGenerator pdfGenerator;

    public ReportingController(ReportingApplicationService reportingService,
                               StatementPdfGenerator pdfGenerator) {
        this.reportingService = reportingService;
        this.pdfGenerator     = pdfGenerator;
    }

    // ── Account Statement (JSON) ──────────────────────────────────────────

    @GetMapping("/accounts/{accountId}/statement")
    @Operation(summary = "Get account statement as JSON for a date range")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'COMPLIANCE')")
    public AccountStatement getStatement(
        @PathVariable String accountId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return reportingService.buildStatement(accountId, from, to);
    }

    // ── Account Statement (PDF download) ─────────────────────────────────

    @GetMapping(value = "/accounts/{accountId}/statement/pdf",
                produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download account statement as a PDF")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'COMPLIANCE')")
    public ResponseEntity<byte[]> getStatementPdf(
        @PathVariable String accountId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        byte[] pdf = reportingService.buildStatementPdf(accountId, from, to, pdfGenerator);

        String filename = "statement_" + accountId + "_"
            + FILE_DATE.format(from) + "_" + FILE_DATE.format(to) + ".pdf";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdf.length)
            .body(pdf);
    }

    // ── Trial Balance ─────────────────────────────────────────────────────

    @GetMapping("/trial-balance")
    @Operation(summary = "Generate trial balance across all accounts (Admin/Compliance only)")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public TrialBalance getTrialBalance(
        @RequestParam String currency,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf
    ) {
        Instant effectiveAsOf = asOf != null ? asOf : Instant.now();
        return reportingService.buildTrialBalance(currency, effectiveAsOf);
    }
}
