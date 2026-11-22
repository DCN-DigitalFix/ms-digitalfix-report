package cl.duoc.ms_digitalfix_report.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.ms_digitalfix_report.dto.CertificateView;
import cl.duoc.ms_digitalfix_report.dto.ReportSummaryView;
import cl.duoc.ms_digitalfix_report.service.OrderSummaryService;

/**
 * Solo lectura: report no crea ni modifica ordenes, solo resume lo que
 * workorders publica.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final OrderSummaryService service;

    @Autowired
    public ReportController(OrderSummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('Admin', 'Supervisor', 'Auditor') and hasAuthority('SCOPE_OT.Read')")
    public ResponseEntity<ReportSummaryView> resumen() {
        return ResponseEntity.ok(service.resumen());
    }

    @GetMapping("/orders/{ordenId}/certificate")
    @PreAuthorize("hasAnyRole('Admin', 'Supervisor', 'Auditor') and hasAuthority('SCOPE_OT.Read')")
    public ResponseEntity<CertificateView> certificado(@PathVariable String ordenId) {
        return ResponseEntity.ok(service.certificado(ordenId));
    }
}
