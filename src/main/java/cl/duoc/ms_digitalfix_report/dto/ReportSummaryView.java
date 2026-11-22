package cl.duoc.ms_digitalfix_report.dto;

import java.util.Map;

/**
 * @param ordenesPorEstado   conteo de ordenes agrupadas por su estado actual
 * @param total              cantidad total de ordenes con al menos un evento registrado
 * @param tiempoPromedioCierreHoras promedio de horas entre creacion y cierre,
 *                           solo entre las ordenes CERRADA; null si ninguna cerro todavia
 * @param totalEventosAuditoria cantidad de eventos unicos recibidos desde
 *                           digitalfix.audit.events (ver AuditTrailService)
 */
public record ReportSummaryView(
        Map<String, Long> ordenesPorEstado,
        long total,
        Double tiempoPromedioCierreHoras,
        long totalEventosAuditoria) {
}
