package cl.duoc.ms_digitalfix_report.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cl.duoc.ms_digitalfix_report.exception.ErrorNoRecuperableException;

/**
 * Convierte el valor crudo del ConsumerRecord (texto JSON) en WorkOrderEvent y
 * valida que traiga los campos obligatorios. Igual razonamiento que en
 * ms-digitalfix-audit: se hace fuera del deserializador de Kafka para que un
 * mensaje invalido no deje el consumidor atascado.
 */
@Component
public class WorkOrderEventParser {

    private final ObjectMapper mapper;

    @Autowired
    public WorkOrderEventParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public WorkOrderEvent parsear(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new ErrorNoRecuperableException("El mensaje llego vacio");
        }

        WorkOrderEvent evento;
        try {
            evento = mapper.readValue(payload, WorkOrderEvent.class);
        } catch (JsonProcessingException e) {
            throw new ErrorNoRecuperableException(
                    "El mensaje no es un JSON valido: " + e.getOriginalMessage(), e);
        }

        if (evento == null || !evento.esValido()) {
            throw new ErrorNoRecuperableException(
                    "El evento no trae los campos obligatorios "
                            + "(eventId, eventType, ordenId, estadoNuevo, occurredAt): " + payload);
        }

        return evento;
    }
}
