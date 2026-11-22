package cl.duoc.ms_digitalfix_report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import cl.duoc.ms_digitalfix_report.exception.ErrorRecuperableException;
import cl.duoc.ms_digitalfix_report.messaging.AuditRecordEvent;
import cl.duoc.ms_digitalfix_report.repository.AuditTrailEventRepository;

@ExtendWith(MockitoExtension.class)
class AuditTrailServiceTest {

    @Mock
    private AuditTrailEventRepository repository;

    private AuditTrailService service;

    private final AuditRecordEvent evento = new AuditRecordEvent(
            "ar-1", "e-1", "OT-0001", "ORDEN_CREADA", null, "INGRESADA",
            Instant.parse("2026-11-22T10:00:00Z"), Instant.parse("2026-11-22T10:00:01Z"), "trace-1");

    @org.junit.jupiter.api.BeforeEach
    void crearServicio() {
        service = new AuditTrailService(repository);
    }

    @Test
    void registraUnEventoNuevo() {
        when(repository.existsById("ar-1")).thenReturn(false);

        service.registrar(evento);

        verify(repository).saveAndFlush(any());
    }

    @Test
    void descartaUnDuplicadoConocidoSinGuardarloDeNuevo() {
        when(repository.existsById("ar-1")).thenReturn(true);

        service.registrar(evento);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void descartaUnDuplicadoDeCarreraSinPropagarElError() {
        when(repository.existsById("ar-1")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicado"));

        service.registrar(evento);
        // no lanza: el metodo retorna normalmente
    }

    @Test
    void unFalloTransitorioDeLaBaseSeConvierteEnErrorRecuperable() {
        when(repository.existsById("ar-1")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("sin conexion"));

        assertThatThrownBy(() -> service.registrar(evento))
                .isInstanceOf(ErrorRecuperableException.class);
    }

    @Test
    void totalDelegaEnElConteoDelRepositorio() {
        when(repository.count()).thenReturn(42L);

        assertThat(service.total()).isEqualTo(42L);
    }
}
