package cl.duoc.ms_digitalfix_report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Topicos y ajustes de consumo, leidos desde application.yaml bajo
 * "digitalfix.kafka". Ninguna clase escribe un nombre de topico a mano.
 */
@ConfigurationProperties(prefix = "digitalfix.kafka")
public record DigitalfixKafkaProperties(Topics topics, Consumer consumer) {

    /**
     * @param workordersEvents topico de eventos de dominio de workorders (grupo report-metrics)
     * @param auditEvents      topico de registros normalizados de audit (grupo report-audit-trail)
     */
    public record Topics(String workordersEvents, String auditEvents) {}

    /**
     * @param concurrencia         numero de hilos del listener (una por particion)
     * @param maxReintentos        intentos para un error recuperable antes de la DLT
     * @param backoffInicialMs     espera antes del primer reintento
     * @param backoffMultiplicador factor de crecimiento de la espera entre reintentos
     * @param backoffMaximoMs      tope de la espera entre reintentos
     */
    public record Consumer(
            int concurrencia,
            int maxReintentos,
            long backoffInicialMs,
            double backoffMultiplicador,
            long backoffMaximoMs) {}
}
