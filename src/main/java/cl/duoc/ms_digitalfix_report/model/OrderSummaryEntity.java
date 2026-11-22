package cl.duoc.ms_digitalfix_report.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Estado actual de una orden de trabajo para efectos de reporteria. A
 * diferencia de audit_events en ms-digitalfix-audit (que es un historial de
 * eventos, uno por fila), esta tabla guarda una sola fila por orden y cada
 * evento la actualiza: es una vista materializada del ultimo estado conocido,
 * no un log.
 *
 * ultimoEventoOccurredAt es lo que permite que la actualizacion sea
 * idempotente ante reentregas y desorden: OrderSummaryService solo aplica un
 * evento si su occurredAt es mas nuevo que el ya guardado aqui.
 */
@Entity
@Table(name = "order_summary")
public class OrderSummaryEntity {

    @Id
    @Column(name = "orden_id", nullable = false, length = 20)
    private String ordenId;

    @Column(name = "cliente", length = 120)
    private String cliente;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "tecnico_asignado", length = 120)
    private String tecnicoAsignado;

    @Column(name = "creada_en")
    private Instant creadaEn;

    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    @Column(name = "ultimo_evento_occurred_at", nullable = false)
    private Instant ultimoEventoOccurredAt;

    public OrderSummaryEntity() {
    }

    public OrderSummaryEntity(String ordenId, String cliente, String estado, String tecnicoAsignado,
                               Instant creadaEn, Instant cerradaEn, Instant ultimoEventoOccurredAt) {
        this.ordenId = ordenId;
        this.cliente = cliente;
        this.estado = estado;
        this.tecnicoAsignado = tecnicoAsignado;
        this.creadaEn = creadaEn;
        this.cerradaEn = cerradaEn;
        this.ultimoEventoOccurredAt = ultimoEventoOccurredAt;
    }

    public String getOrdenId() { return ordenId; }
    public void setOrdenId(String ordenId) { this.ordenId = ordenId; }
    public String getCliente() { return cliente; }
    public void setCliente(String cliente) { this.cliente = cliente; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTecnicoAsignado() { return tecnicoAsignado; }
    public void setTecnicoAsignado(String tecnicoAsignado) { this.tecnicoAsignado = tecnicoAsignado; }
    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
    public Instant getCerradaEn() { return cerradaEn; }
    public void setCerradaEn(Instant cerradaEn) { this.cerradaEn = cerradaEn; }
    public Instant getUltimoEventoOccurredAt() { return ultimoEventoOccurredAt; }
    public void setUltimoEventoOccurredAt(Instant ultimoEventoOccurredAt) { this.ultimoEventoOccurredAt = ultimoEventoOccurredAt; }
}
