package cl.duoc.ms_digitalfix_report.messaging;

import java.time.Instant;

/**
 * Contrato del evento publicado por ms-digitalfix-audit en
 * digitalfix.audit.events, despues de que audit guarda cada WorkOrderEvent.
 * Se duplica el record aqui a proposito, igual que WorkOrderEvent: el
 * contrato es el JSON, no la clase Java.
 *
 * @param eventId       identificador propio de este evento (lo genera audit)
 * @param origenEventId eventId del WorkOrderEvent que lo origino
 * @param ordenId       orden de trabajo afectada; tambien es la clave del mensaje
 * @param eventType     ORDEN_CREADA | ESTADO_CAMBIADO
 * @param occurredAt    momento del hecho de negocio original
 * @param registradoEn  momento en que audit lo guardo
 */
public record AuditRecordEvent(
        String eventId,
        String origenEventId,
        String ordenId,
        String eventType,
        String estadoAnterior,
        String estadoNuevo,
        Instant occurredAt,
        Instant registradoEn,
        String traceId) {

    public boolean esValido() {
        return noVacio(eventId) && noVacio(ordenId);
    }

    private static boolean noVacio(String valor) {
        return valor != null && !valor.isBlank();
    }
}
