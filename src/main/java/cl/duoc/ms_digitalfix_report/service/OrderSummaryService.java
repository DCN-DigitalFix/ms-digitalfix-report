package cl.duoc.ms_digitalfix_report.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import cl.duoc.ms_digitalfix_report.dto.CertificateView;
import cl.duoc.ms_digitalfix_report.dto.ReportSummaryView;
import cl.duoc.ms_digitalfix_report.exception.ErrorRecuperableException;
import cl.duoc.ms_digitalfix_report.exception.NotFoundException;
import cl.duoc.ms_digitalfix_report.exception.OrdenNoCerradaException;
import cl.duoc.ms_digitalfix_report.messaging.WorkOrderEvent;
import cl.duoc.ms_digitalfix_report.model.OrderSummaryEntity;
import cl.duoc.ms_digitalfix_report.repository.OrderSummaryRepository;

/**
 * Mantiene una fila por orden con su estado mas reciente (ver
 * OrderSummaryEntity) y resuelve los dos reportes que expone el controlador.
 *
 * La idempotencia aqui es distinta de ms-digitalfix-audit: alla cada evento es
 * una fila nueva y la clave primaria (eventId) impide duplicarla. Aqui cada
 * evento actualiza la misma fila (la orden), asi que la proteccion es
 * comparar occurredAt: un evento solo se aplica si es mas nuevo que el ultimo
 * ya guardado. Esto tambien cubre el reenvio exacto del mismo evento (entrega
 * al menos una vez de Kafka), porque "mas nuevo que si mismo" es falso.
 */
@Service
public class OrderSummaryService {

    private static final Logger log = LoggerFactory.getLogger(OrderSummaryService.class);

    private static final String ESTADO_CERRADA = "CERRADA";
    private static final String EVENTO_ORDEN_CREADA = "ORDEN_CREADA";

    private final OrderSummaryRepository repository;
    private final AuditTrailService auditTrailService;

    @Autowired
    public OrderSummaryService(OrderSummaryRepository repository, AuditTrailService auditTrailService) {
        this.repository = repository;
        this.auditTrailService = auditTrailService;
    }

    public void aplicar(WorkOrderEvent evento) {
        OrderSummaryEntity resumen = repository.findById(evento.ordenId())
                .orElseGet(() -> new OrderSummaryEntity(
                        evento.ordenId(), null, evento.estadoNuevo(), null, null, null, null));

        if (resumen.getUltimoEventoOccurredAt() != null
                && !evento.occurredAt().isAfter(resumen.getUltimoEventoOccurredAt())) {
            log.info("[report] evento {} de la orden {} no es mas nuevo que el ya aplicado, se ignora",
                    evento.eventId(), evento.ordenId());
            return;
        }

        if (EVENTO_ORDEN_CREADA.equals(evento.eventType()) && resumen.getCreadaEn() == null) {
            resumen.setCreadaEn(evento.occurredAt());
        }
        if (evento.cliente() != null) {
            resumen.setCliente(evento.cliente());
        }
        if (evento.tecnicoAsignado() != null) {
            resumen.setTecnicoAsignado(evento.tecnicoAsignado());
        }
        resumen.setEstado(evento.estadoNuevo());
        if (ESTADO_CERRADA.equals(evento.estadoNuevo())) {
            resumen.setCerradaEn(evento.occurredAt());
        }
        resumen.setUltimoEventoOccurredAt(evento.occurredAt());

        try {
            repository.save(resumen);
        } catch (DataAccessException e) {
            throw new ErrorRecuperableException(
                    "No se pudo guardar el resumen de la orden " + evento.ordenId(), e);
        }

        log.info("[report] orden {} actualizada a estado {}", evento.ordenId(), evento.estadoNuevo());
    }

    public ReportSummaryView resumen() {
        List<OrderSummaryEntity> ordenes = repository.findAll();

        Map<String, Long> porEstado = ordenes.stream()
                .collect(Collectors.groupingBy(OrderSummaryEntity::getEstado, Collectors.counting()));

        List<Duration> duraciones = ordenes.stream()
                .filter(o -> o.getCreadaEn() != null && o.getCerradaEn() != null)
                .map(o -> Duration.between(o.getCreadaEn(), o.getCerradaEn()))
                .toList();

        Double promedioHoras = duraciones.isEmpty()
                ? null
                : duraciones.stream().mapToDouble(d -> d.toMinutes() / 60.0).average().orElseThrow();

        return new ReportSummaryView(porEstado, ordenes.size(), promedioHoras, auditTrailService.total());
    }

    public CertificateView certificado(String ordenId) {
        OrderSummaryEntity resumen = repository.findById(ordenId)
                .orElseThrow(() -> new NotFoundException(
                        "No hay informacion registrada para la orden " + ordenId));

        if (!ESTADO_CERRADA.equals(resumen.getEstado())) {
            throw new OrdenNoCerradaException(
                    "La orden " + ordenId + " aun no esta cerrada (estado actual: "
                            + resumen.getEstado() + ")");
        }

        Double duracionHoras = (resumen.getCreadaEn() != null && resumen.getCerradaEn() != null)
                ? Duration.between(resumen.getCreadaEn(), resumen.getCerradaEn()).toMinutes() / 60.0
                : null;

        return new CertificateView(
                resumen.getOrdenId(),
                resumen.getCliente(),
                resumen.getTecnicoAsignado(),
                resumen.getCreadaEn(),
                resumen.getCerradaEn(),
                duracionHoras);
    }
}
