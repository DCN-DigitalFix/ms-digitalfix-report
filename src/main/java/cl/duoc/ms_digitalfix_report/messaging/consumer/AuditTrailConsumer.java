package cl.duoc.ms_digitalfix_report.messaging.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import cl.duoc.ms_digitalfix_report.messaging.AuditRecordEvent;
import cl.duoc.ms_digitalfix_report.messaging.AuditRecordEventParser;
import cl.duoc.ms_digitalfix_report.service.AuditTrailService;

/**
 * Segundo consumidor de report: digitalfix.audit.events, con el grupo
 * "report-audit-trail" (distinto de "report-metrics", que lee
 * digitalfix.workorders.events). El groupId se fija aqui, en la anotacion,
 * porque @KafkaListener lo toma con prioridad sobre el group.id de la
 * ConsumerFactory compartida (ver KafkaConsumerConfig); no hace falta una
 * fabrica nueva para esto.
 *
 * El manejo de errores es el mismo DefaultErrorHandler de
 * KafkaConsumerConfig: su DeadLetterPublishingRecoverer arma el nombre de la
 * DLT a partir de record.topic(), asi que sirve igual para este topico
 * (digitalfix.audit.events.DLT) sin configuracion adicional.
 */
@Component
public class AuditTrailConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailConsumer.class);

    public static final String CONSUMER_GROUP = "report-audit-trail";

    private final AuditRecordEventParser parser;
    private final AuditTrailService service;

    @Autowired
    public AuditTrailConsumer(AuditRecordEventParser parser, AuditTrailService service) {
        this.parser = parser;
        this.service = service;
    }

    @KafkaListener(topics = "${digitalfix.kafka.topics.audit-events}", groupId = CONSUMER_GROUP)
    public void recibir(ConsumerRecord<String, String> record) {
        AuditRecordEvent evento = parser.parsear(record.value());

        service.registrar(evento);

        log.info("[report] evento de auditoria {} de la orden {} consumido de la particion {}",
                evento.eventId(), evento.ordenId(), record.partition());
    }
}
