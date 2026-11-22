package cl.duoc.ms_digitalfix_report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import cl.duoc.ms_digitalfix_report.dto.CertificateView;
import cl.duoc.ms_digitalfix_report.dto.ReportSummaryView;
import cl.duoc.ms_digitalfix_report.exception.ErrorRecuperableException;
import cl.duoc.ms_digitalfix_report.exception.NotFoundException;
import cl.duoc.ms_digitalfix_report.exception.OrdenNoCerradaException;
import cl.duoc.ms_digitalfix_report.messaging.WorkOrderEvent;
import cl.duoc.ms_digitalfix_report.model.OrderSummaryEntity;
import cl.duoc.ms_digitalfix_report.repository.OrderSummaryRepository;

@ExtendWith(MockitoExtension.class)
class OrderSummaryServiceTest {

    @Mock
    private OrderSummaryRepository repository;

    @Mock
    private AuditTrailService auditTrailService;

    private OrderSummaryService service;

    private final Instant t0 = Instant.parse("2026-11-20T10:00:00Z");
    private final Instant t1 = Instant.parse("2026-11-21T10:00:00Z");

    @org.junit.jupiter.api.BeforeEach
    void crearServicio() {
        service = new OrderSummaryService(repository, auditTrailService);
    }

    @Test
    void creaUnResumenNuevoConElPrimerEvento() {
        when(repository.findById("OT-0001")).thenReturn(Optional.empty());

        WorkOrderEvent evento = new WorkOrderEvent(
                "e-1", "ORDEN_CREADA", t0, "trace-1", "OT-0001",
                null, "INGRESADA", "Cliente Prueba", null, "sistema");

        service.aplicar(evento);

        verify(repository).save(any(OrderSummaryEntity.class));
    }

    @Test
    void actualizaElEstadoConUnEventoMasNuevo() {
        OrderSummaryEntity existente = new OrderSummaryEntity(
                "OT-0001", "Cliente Prueba", "INGRESADA", null, t0, null, t0);
        when(repository.findById("OT-0001")).thenReturn(Optional.of(existente));

        WorkOrderEvent evento = new WorkOrderEvent(
                "e-2", "ESTADO_CAMBIADO", t1, "trace-1", "OT-0001",
                "INGRESADA", "ASIGNADA", null, "Tecnico Uno", "supervisor@digitalfix.cl");

        service.aplicar(evento);

        assertThat(existente.getEstado()).isEqualTo("ASIGNADA");
        assertThat(existente.getTecnicoAsignado()).isEqualTo("Tecnico Uno");
        verify(repository).save(existente);
    }

    @Test
    void ignoraUnEventoMasViejoQueElYaAplicado() {
        OrderSummaryEntity existente = new OrderSummaryEntity(
                "OT-0001", "Cliente Prueba", "ASIGNADA", "Tecnico Uno", t0, null, t1);
        when(repository.findById("OT-0001")).thenReturn(Optional.of(existente));

        WorkOrderEvent eventoViejo = new WorkOrderEvent(
                "e-0", "ORDEN_CREADA", t0, "trace-0", "OT-0001",
                null, "INGRESADA", "Cliente Prueba", null, "sistema");

        service.aplicar(eventoViejo);

        assertThat(existente.getEstado()).isEqualTo("ASIGNADA");
        verify(repository, never()).save(any());
    }

    @Test
    void ignoraElReenvioExactoDelMismoEvento() {
        OrderSummaryEntity existente = new OrderSummaryEntity(
                "OT-0001", "Cliente Prueba", "ASIGNADA", "Tecnico Uno", t0, null, t1);
        when(repository.findById("OT-0001")).thenReturn(Optional.of(existente));

        WorkOrderEvent mismoEvento = new WorkOrderEvent(
                "e-2", "ESTADO_CAMBIADO", t1, "trace-1", "OT-0001",
                "INGRESADA", "ASIGNADA", null, "Tecnico Uno", "supervisor@digitalfix.cl");

        service.aplicar(mismoEvento);

        verify(repository, never()).save(any());
    }

    @Test
    void cerrarUnaOrdenRegistraCerradaEn() {
        OrderSummaryEntity existente = new OrderSummaryEntity(
                "OT-0001", "Cliente Prueba", "EN_EJECUCION", "Tecnico Uno", t0, null, t0);
        when(repository.findById("OT-0001")).thenReturn(Optional.of(existente));

        WorkOrderEvent evento = new WorkOrderEvent(
                "e-3", "ESTADO_CAMBIADO", t1, "trace-1", "OT-0001",
                "EN_EJECUCION", "CERRADA", null, null, "tecnico@digitalfix.cl");

        service.aplicar(evento);

        assertThat(existente.getEstado()).isEqualTo("CERRADA");
        assertThat(existente.getCerradaEn()).isEqualTo(t1);
    }

    @Test
    void unFalloTransitorioDeLaBaseSeConvierteEnErrorRecuperable() {
        when(repository.findById("OT-0001")).thenReturn(Optional.empty());
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("sin conexion"));

        WorkOrderEvent evento = new WorkOrderEvent(
                "e-1", "ORDEN_CREADA", t0, "trace-1", "OT-0001",
                null, "INGRESADA", "Cliente Prueba", null, "sistema");

        assertThatThrownBy(() -> service.aplicar(evento))
                .isInstanceOf(ErrorRecuperableException.class);
    }

    @Test
    void resumenAgrupaPorEstadoYCalculaElPromedioDeCierre() {
        OrderSummaryEntity cerrada1 = new OrderSummaryEntity(
                "OT-0001", "Cliente A", "CERRADA", "T1", t0, t0.plusSeconds(3600), t0.plusSeconds(3600));
        OrderSummaryEntity cerrada2 = new OrderSummaryEntity(
                "OT-0002", "Cliente B", "CERRADA", "T2", t0, t0.plusSeconds(7200), t0.plusSeconds(7200));
        OrderSummaryEntity enCurso = new OrderSummaryEntity(
                "OT-0003", "Cliente C", "ASIGNADA", "T3", t0, null, t0);

        when(repository.findAll()).thenReturn(List.of(cerrada1, cerrada2, enCurso));
        when(auditTrailService.total()).thenReturn(7L);

        ReportSummaryView resumen = service.resumen();

        assertThat(resumen.total()).isEqualTo(3);
        assertThat(resumen.ordenesPorEstado()).containsEntry("CERRADA", 2L);
        assertThat(resumen.ordenesPorEstado()).containsEntry("ASIGNADA", 1L);
        assertThat(resumen.tiempoPromedioCierreHoras()).isEqualTo(1.5);
        assertThat(resumen.totalEventosAuditoria()).isEqualTo(7L);
    }

    @Test
    void resumenSinOrdenesCerradasNoCalculaPromedio() {
        OrderSummaryEntity enCurso = new OrderSummaryEntity(
                "OT-0001", "Cliente A", "ASIGNADA", "T1", t0, null, t0);
        when(repository.findAll()).thenReturn(List.of(enCurso));

        ReportSummaryView resumen = service.resumen();

        assertThat(resumen.tiempoPromedioCierreHoras()).isNull();
    }

    @Test
    void certificadoDeUnaOrdenCerrada() {
        OrderSummaryEntity cerrada = new OrderSummaryEntity(
                "OT-0001", "Cliente A", "CERRADA", "T1", t0, t0.plusSeconds(7200), t0.plusSeconds(7200));
        when(repository.findById("OT-0001")).thenReturn(Optional.of(cerrada));

        CertificateView certificado = service.certificado("OT-0001");

        assertThat(certificado.ordenId()).isEqualTo("OT-0001");
        assertThat(certificado.duracionHoras()).isEqualTo(2.0);
    }

    @Test
    void certificadoDeUnaOrdenNoCerradaLanzaConflicto() {
        OrderSummaryEntity enCurso = new OrderSummaryEntity(
                "OT-0001", "Cliente A", "ASIGNADA", "T1", t0, null, t0);
        when(repository.findById("OT-0001")).thenReturn(Optional.of(enCurso));

        assertThatThrownBy(() -> service.certificado("OT-0001"))
                .isInstanceOf(OrdenNoCerradaException.class);
    }

    @Test
    void certificadoDeUnaOrdenDesconocidaLanzaNotFound() {
        when(repository.findById("OT-9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.certificado("OT-9999"))
                .isInstanceOf(NotFoundException.class);
    }
}
