package br.com.stockshift.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    // Support for Java 8 date/time types
    mapper.registerModule(new JavaTimeModule());
    // Disable writing dates as timestamps
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }
}
