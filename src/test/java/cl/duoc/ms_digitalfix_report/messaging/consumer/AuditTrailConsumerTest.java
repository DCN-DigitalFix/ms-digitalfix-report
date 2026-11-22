package cl.duoc.ms_digitalfix_report.messaging.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.ms_digitalfix_report.messaging.AuditRecordEvent;
import cl.duoc.ms_digitalfix_report.messaging.AuditRecordEventParser;
import cl.duoc.ms_digitalfix_report.service.AuditTrailService;

@ExtendWith(MockitoExtension.class)
class AuditTrailConsumerTest {

    @Mock
    private AuditRecordEventParser parser;

    @Mock
    private AuditTrailService service;

    @Test
    void delegaElParseoYElRegistroEnOrden() {
        AuditTrailConsumer consumer = new AuditTrailConsumer(parser, service);

        AuditRecordEvent evento = new AuditRecordEvent(
                "ar-1", "e-1", "OT-0001", "ORDEN_CREADA", null, "INGRESADA",
                Instant.now(), Instant.now(), "trace-1");

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("digitalfix.audit.events", 0, 5L, "OT-0001", "{...}");

        when(parser.parsear("{...}")).thenReturn(evento);

        consumer.recibir(record);

        verify(parser).parsear(eq("{...}"));
        verify(service).registrar(any(AuditRecordEvent.class));
    }

    @Test
    void unPayloadInvalidoSePropagaSinLlamarAlServicio() {
        AuditTrailConsumer consumer = new AuditTrailConsumer(parser, service);

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("digitalfix.audit.events", 0, 6L, "OT-0002", "{no es json");

        when(parser.parsear("{no es json"))
                .thenThrow(new cl.duoc.ms_digitalfix_report.exception.ErrorNoRecuperableException("invalido"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.recibir(record))
                .isInstanceOf(cl.duoc.ms_digitalfix_report.exception.ErrorNoRecuperableException.class);

        verify(service, org.mockito.Mockito.never()).registrar(any());
    }
}
