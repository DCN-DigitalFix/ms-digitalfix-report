package cl.duoc.ms_digitalfix_report.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cl.duoc.ms_digitalfix_report.exception.ErrorNoRecuperableException;

/**
 * Convierte el valor crudo del ConsumerRecord de digitalfix.audit.events en
 * AuditRecordEvent. Mismo razonamiento que WorkOrderEventParser: se parsea
 * fuera del deserializador de Kafka para que un mensaje invalido no deje el
 * consumidor atascado.
 */
@Component
public class AuditRecordEventParser {

    private final ObjectMapper mapper;

    @Autowired
    public AuditRecordEventParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public AuditRecordEvent parsear(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new ErrorNoRecuperableException("El mensaje llego vacio");
        }

        AuditRecordEvent evento;
        try {
            evento = mapper.readValue(payload, AuditRecordEvent.class);
        } catch (JsonProcessingException e) {
            throw new ErrorNoRecuperableException(
                    "El mensaje no es un JSON valido: " + e.getOriginalMessage(), e);
        }

        if (evento == null || !evento.esValido()) {
            throw new ErrorNoRecuperableException(
                    "El evento no trae los campos obligatorios (eventId, ordenId): " + payload);
        }

        return evento;
    }
}
