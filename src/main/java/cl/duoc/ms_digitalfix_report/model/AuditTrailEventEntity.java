package cl.duoc.ms_digitalfix_report.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un registro por evento de digitalfix.audit.events que report ya proceso.
 * La clave primaria (eventId) es lo que hace idempotente el conteo: Kafka
 * entrega al menos una vez, y un contador en memoria se duplicaria ante una
 * reentrega; con esta tabla, repository.count() siempre refleja eventos
 * unicos, igual que audit_events en ms-digitalfix-audit.
 */
@Entity
@Table(name = "audit_trail_event")
public class AuditTrailEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "orden_id", nullable = false, length = 20)
    private String ordenId;

    @Column(name = "event_type", length = 40)
    private String eventType;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    public AuditTrailEventEntity() {
    }

    public AuditTrailEventEntity(String eventId, String ordenId, String eventType, Instant registradoEn) {
        this.eventId = eventId;
        this.ordenId = ordenId;
        this.eventType = eventType;
        this.registradoEn = registradoEn;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getOrdenId() { return ordenId; }
    public void setOrdenId(String ordenId) { this.ordenId = ordenId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getRegistradoEn() { return registradoEn; }
    public void setRegistradoEn(Instant registradoEn) { this.registradoEn = registradoEn; }
}
