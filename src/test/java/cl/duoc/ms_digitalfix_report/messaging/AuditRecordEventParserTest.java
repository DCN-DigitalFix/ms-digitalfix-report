package cl.duoc.ms_digitalfix_report.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import cl.duoc.ms_digitalfix_report.exception.ErrorNoRecuperableException;

class AuditRecordEventParserTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // AuditRecordEventParser se inyecta por campo: no tiene constructor que invocar.
    private final AuditRecordEventParser parser = crearParser();

    private AuditRecordEventParser crearParser() {
        AuditRecordEventParser p = new AuditRecordEventParser();
        ReflectionTestUtils.setField(p, "mapper", mapper);
        return p;
    }

    @Test
    void parseaUnRegistroValido() {
        String payload = """
                {
                  "eventId": "ar-1",
                  "origenEventId": "e-1",
                  "ordenId": "OT-0001",
                  "eventType": "ORDEN_CREADA",
                  "estadoNuevo": "INGRESADA",
                  "occurredAt": "2026-11-22T10:00:00Z",
                  "registradoEn": "2026-11-22T10:00:01Z"
                }
                """;

        AuditRecordEvent evento = parser.parsear(payload);

        assertThat(evento.eventId()).isEqualTo("ar-1");
        assertThat(evento.ordenId()).isEqualTo("OT-0001");
    }

    @Test
    void rechazaUnJsonMalFormado() {
        assertThatThrownBy(() -> parser.parsear("{no es json"))
                .isInstanceOf(ErrorNoRecuperableException.class);
    }

    @Test
    void rechazaUnRegistroSinOrdenId() {
        String payload = """
                {"eventId": "ar-2"}
                """;

        assertThatThrownBy(() -> parser.parsear(payload))
                .isInstanceOf(ErrorNoRecuperableException.class);
    }

    @Test
    void rechazaUnMensajeVacio() {
        assertThatThrownBy(() -> parser.parsear(" "))
                .isInstanceOf(ErrorNoRecuperableException.class);
    }
}
