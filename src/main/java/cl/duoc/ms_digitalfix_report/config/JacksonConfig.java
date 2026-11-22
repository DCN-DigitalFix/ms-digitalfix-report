package cl.duoc.ms_digitalfix_report.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Se declara el ObjectMapper a mano, igual que en ms-digitalfix-audit y
 * ms-digitalfix-workorders: no hay garantia de que la autoconfiguracion de
 * Jackson registre un bean autoescaneable solo por tener jackson-databind en
 * el classpath, y WorkOrderEventParser lo necesita inyectado.
 *
 * FAIL_ON_UNKNOWN_PROPERTIES en false porque workorders puede agregar campos
 * al evento antes de que report los use.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
