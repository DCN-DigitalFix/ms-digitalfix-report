package cl.duoc.ms_digitalfix_report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import cl.duoc.ms_digitalfix_report.exception.ErrorRecuperableException;
import cl.duoc.ms_digitalfix_report.messaging.AuditRecordEvent;
import cl.duoc.ms_digitalfix_report.model.AuditTrailEventEntity;
import cl.duoc.ms_digitalfix_report.repository.AuditTrailEventRepository;

/**
 * Lo que report hace con digitalfix.audit.events: llevar la cuenta de
 * cuantos eventos de auditoria existen, para mostrarla en el resumen
 * (totalEventosAuditoria). No duplica el historial de audit; solo registra
 * el eventId para poder contar sin contar dos veces una reentrega.
 */
@Service
public class AuditTrailService {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

    private final AuditTrailEventRepository repository;

    @Autowired
    public AuditTrailService(AuditTrailEventRepository repository) {
        this.repository = repository;
    }

    public void registrar(AuditRecordEvent evento) {
        if (repository.existsById(evento.eventId())) {
            log.info("[report] evento de auditoria {} ya contado, se descarta el duplicado", evento.eventId());
            return;
        }

        AuditTrailEventEntity entidad = new AuditTrailEventEntity(
                evento.eventId(), evento.ordenId(), evento.eventType(), java.time.Instant.now());

        try {
            repository.saveAndFlush(entidad);
        } catch (DataIntegrityViolationException e) {
            log.info("[report] evento de auditoria {} llego duplicado en una carrera, se descarta",
                    evento.eventId());
            return;
        } catch (DataAccessException e) {
            throw new ErrorRecuperableException(
                    "No se pudo registrar el evento de auditoria " + evento.eventId(), e);
        }

        log.info("[report] evento de auditoria {} de la orden {} contado", evento.eventId(), evento.ordenId());
    }

    public long total() {
        return repository.count();
    }
}
