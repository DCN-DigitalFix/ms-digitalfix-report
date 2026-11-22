package cl.duoc.ms_digitalfix_report.messaging.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import cl.duoc.ms_digitalfix_report.messaging.WorkOrderEvent;
import cl.duoc.ms_digitalfix_report.messaging.WorkOrderEventParser;
import cl.duoc.ms_digitalfix_report.service.OrderSummaryService;

/**
 * Consume digitalfix.workorders.events con el grupo "report-metrics" (fijado
 * en KafkaConsumerConfig), el mismo topico que lee ms-digitalfix-audit con el
 * grupo "audit-timeline". Ninguno de los dos le quita mensajes al otro.
 *
 * El parseo y la actualizacion son los unicos dos pasos; ninguna excepcion se
 * atrapa aqui porque es el CommonErrorHandler del contenedor (ver
 * KafkaConsumerConfig) quien decide reintentar o derivar a la DLT.
 */
@Component
public class WorkOrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkOrderEventConsumer.class);

    private final WorkOrderEventParser parser;
    private final OrderSummaryService service;

    @Autowired
    public WorkOrderEventConsumer(WorkOrderEventParser parser, OrderSummaryService service) {
        this.parser = parser;
        this.service = service;
    }

    @KafkaListener(topics = "${digitalfix.kafka.topics.workorders-events}")
    public void recibir(ConsumerRecord<String, String> record) {
        WorkOrderEvent evento = parser.parsear(record.value());

        service.aplicar(evento);

        log.info("[report] evento {} de la orden {} consumido de la particion {}",
                evento.eventId(), evento.ordenId(), record.partition());
    }
}
