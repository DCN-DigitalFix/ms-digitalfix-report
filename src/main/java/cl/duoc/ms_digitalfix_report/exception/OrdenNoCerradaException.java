package cl.duoc.ms_digitalfix_report.exception;

/** La orden existe pero todavia no llega a CERRADA: no hay certificado que emitir. */
public class OrdenNoCerradaException extends RuntimeException {
    public OrdenNoCerradaException(String message) {
        super(message);
    }
}
