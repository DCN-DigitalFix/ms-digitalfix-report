package cl.duoc.ms_digitalfix_report.dto;

import java.time.Instant;

/** Datos del certificado de una orden ya CERRADA. */
public record CertificateView(
        String ordenId,
        String cliente,
        String tecnicoAsignado,
        Instant creadaEn,
        Instant cerradaEn,
        Double duracionHoras) {
}
