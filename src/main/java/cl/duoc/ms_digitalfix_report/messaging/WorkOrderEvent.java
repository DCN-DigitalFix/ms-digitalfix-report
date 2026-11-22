package cl.duoc.ms_digitalfix_report.messaging;

import java.time.Instant;

/**
 * Contrato del evento publicado por ms-digitalfix-workorders en
 * digitalfix.workorders.events. Se duplica el record aqui a proposito, igual
 * que en ms-digitalfix-audit: el contrato es el JSON, no la clase Java.
 *
 * @param eventId         identificador unico del evento
 * @param eventType       ORDEN_CREADA | ESTADO_CAMBIADO
 * @param occurredAt      momento en que ocurrio el hecho de negocio; decide si
 *                        un evento es mas nuevo que el estado ya guardado
 * @param traceId         hilo de la peticion HTTP que lo origino
 * @param ordenId         orden de trabajo afectada; tambien es la clave del mensaje en Kafka
 * @param estadoAnterior  null cuando eventType es ORDEN_CREADA
 * @param estadoNuevo     estado resultante
 * @param cliente         nombre del cliente de la orden
 * @param tecnicoAsignado null si todavia no se asigna cuadrilla
 * @param actor           quien origino el cambio (subject del JWT o "sistema")
 */
public record WorkOrderEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String traceId,
        String ordenId,
        String estadoAnterior,
        String estadoNuevo,
        String cliente,
        String tecnicoAsignado,
        String actor) {

    /**
     * Campos sin los que report no puede actualizar el resumen de la orden.
     * El resto son informativos y pueden faltar sin invalidar el mensaje.
     */
    public boolean esValido() {
        return noVacio(eventId)
                && noVacio(eventType)
                && noVacio(ordenId)
                && noVacio(estadoNuevo)
                && occurredAt != null;
    }

    private static boolean noVacio(String valor) {
        return valor != null && !valor.isBlank();
    }
}
