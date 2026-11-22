package cl.duoc.ms_digitalfix_report.exception;

/**
 * Fallo permanente: el mensaje nunca va a poder procesarse tal como viene
 * (JSON invalido o le falta un campo obligatorio). Reintentar solo retrasa su
 * derivacion a la DLT, asi que el error handler la salta por completo.
 */
public class ErrorNoRecuperableException extends RuntimeException {

    public ErrorNoRecuperableException(String message) {
        super(message);
    }

    public ErrorNoRecuperableException(String message, Throwable cause) {
        super(message, cause);
    }
}
