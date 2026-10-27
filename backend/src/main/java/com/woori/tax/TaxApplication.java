package com.woori.tax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import com.fasterxml.jackson.databind.DeserializationFeature;

@SpringBootApplication
public class TaxApplication {
    @Bean Jackson2ObjectMapperBuilderCustomizer rejectFractionalShareCounts() {
        return builder -> builder.featuresToDisable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
    public static void main(String[] args) { SpringApplication.run(TaxApplication.class, args); }
}
