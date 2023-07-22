package com.retisio.arc.listener.handler;

import akka.Done;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import com.retisio.arc.listener.MessageHandler;
import com.retisio.arc.message.brand.BrandMessage;
import com.retisio.arc.repository.brand.BrandRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Slf4j
@Singleton
public class BrandMessageHandler implements MessageHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private BrandRepository brandRepository;

    public BrandMessageHandler(){
        objectMapper.registerModule(new DefaultScalaModule());
    }

    @Override
    public CompletionStage<Done> process(ConsumerRecord<String, String> record) throws Exception {
        String message = record.value();
        log.info("message:: {}", message);
        return process(message);
    }

    public CompletionStage<Done> process(String message) {
        try {
            BrandMessage brandMessage = objectMapper.readValue(message, BrandMessage.class);
            if (Objects.nonNull(brandMessage) && brandMessage.getBrand() !=null && brandMessage.getBrand().getBrandId() != null){
                return brandRepository.saveBrand(brandMessage.getBrand());
            } else {
                log.warn("brandMessage are empty/null, hence skipping the message::{}", message);
            }
        } catch (Exception ex) {
            log.error("ERROR_IN_CONSUMING_BRAND_EVENT : {} WITH_ERROR :", message, ex);
            if (!(ex instanceof JsonMappingException || ex instanceof JsonProcessingException))
                throw new RuntimeException(ex);
        }

        return CompletableFuture.completedFuture((Done.getInstance()));
    }
}
