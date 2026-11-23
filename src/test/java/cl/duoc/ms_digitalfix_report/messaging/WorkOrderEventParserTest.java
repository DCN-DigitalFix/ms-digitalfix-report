package cl.duoc.ms_digitalfix_report.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import cl.duoc.ms_digitalfix_report.exception.ErrorNoRecuperableException;

class WorkOrderEventParserTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // WorkOrderEventParser se inyecta por campo: no tiene constructor que invocar.
    private final WorkOrderEventParser parser = crearParser();

    private WorkOrderEventParser crearParser() {
        WorkOrderEventParser p = new WorkOrderEventParser();
        ReflectionTestUtils.setField(p, "mapper", mapper);
        return p;
    }

    @Test
    void parseaUnMensajeValido() {
        String payload = """
                {
                  "eventId": "e-1",
                  "eventType": "ORDEN_CREADA",
                  "occurredAt": "2026-11-21T22:00:00Z",
                  "ordenId": "OT-0001",
                  "estadoNuevo": "INGRESADA",
                  "cliente": "Cliente Prueba"
                }
                """;

        WorkOrderEvent evento = parser.parsear(payload);

        assertThat(evento.eventId()).isEqualTo("e-1");
        assertThat(evento.ordenId()).isEqualTo("OT-0001");
        assertThat(evento.esValido()).isTrue();
    }

    @Test
    void toleraCamposDesconocidos() {
        String payload = """
                {
                  "eventId": "e-2",
                  "eventType": "ESTADO_CAMBIADO",
                  "occurredAt": "2026-11-21T22:05:00Z",
                  "ordenId": "OT-0002",
                  "estadoAnterior": "INGRESADA",
                  "estadoNuevo": "ASIGNADA",
                  "campoNuevo": "ignorado"
                }
                """;

        WorkOrderEvent evento = parser.parsear(payload);

        assertThat(evento.ordenId()).isEqualTo("OT-0002");
    }

    @Test
    void rechazaUnJsonMalFormado() {
        assertThatThrownBy(() -> parser.parsear("{no es json"))
                .isInstanceOf(ErrorNoRecuperableException.class);
    }

    @Test
    void rechazaUnMensajeSinCamposObligatorios() {
        String payload = """
                {"eventId": "e-3", "eventType": "ORDEN_CREADA"}
                """;

        assertThatThrownBy(() -> parser.parsear(payload))
                .isInstanceOf(ErrorNoRecuperableException.class);
    }

    @Test
    void rechazaUnMensajeVacio() {
        assertThatThrownBy(() -> parser.parsear("  "))
                .isInstanceOf(ErrorNoRecuperableException.class);
    }
}
