package cl.duoc.ms_digitalfix_report.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import cl.duoc.ms_digitalfix_report.exception.ErrorNoRecuperableException;

/**
 * Lado consumidor: fabrica del consumer y manejo de errores.
 *
 * El grupo "report-metrics" es distinto del "audit-timeline" de
 * ms-digitalfix-audit: ambos leen digitalfix.workorders.events completo, cada
 * uno con su propio offset, sin quitarse mensajes entre si. Es la
 * demostracion de consumer groups independientes sobre el mismo topico.
 *
 * Manejo de errores, igual que en audit:
 *
 *   ErrorNoRecuperableException -> directo a la DLT, sin reintentos
 *   cualquier otra excepcion     -> backoff exponencial hasta maxReintentos,
 *                                   luego a la DLT
 */
@Configuration
@EnableConfigurationProperties(DigitalfixKafkaProperties.class)
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    public static final String CONSUMER_GROUP = "report-metrics";

    @Autowired
    private DigitalfixKafkaProperties props;

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

        ExponentialBackOffWithMaxRetries backoff =
                new ExponentialBackOffWithMaxRetries(props.consumer().maxReintentos());
        backoff.setInitialInterval(props.consumer().backoffInicialMs());
        backoff.setMultiplier(props.consumer().backoffMultiplicador());
        backoff.setMaxInterval(props.consumer().backoffMaximoMs());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(ErrorNoRecuperableException.class);
        handler.setRetryListeners((record, ex, intento) -> log.warn(
                "[report] registro de la particion {} del topico {} fallo en el intento {}: {}",
                record.partition(), record.topic(), intento, ex.getMessage()));
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(props.consumer().concurrencia());
        return factory;
    }
}
