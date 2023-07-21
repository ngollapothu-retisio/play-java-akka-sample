package com.retisio.arc.projection.catalog;

import akka.Done;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.persistence.query.typed.EventEnvelope;
import akka.projection.r2dbc.javadsl.R2dbcHandler;
import akka.projection.r2dbc.javadsl.R2dbcSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import com.retisio.arc.aggregate.catalog.Catalog;
import com.retisio.arc.aggregate.catalog.CatalogAggregate;
import com.retisio.arc.aggregate.catalog.CatalogCommand;
import com.retisio.arc.aggregate.catalog.CatalogEvent;
import com.retisio.arc.message.catalog.CatalogMessage;
import com.retisio.arc.util.KafkaUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Slf4j
public class CatalogMessageProjectionHandler extends R2dbcHandler<EventEnvelope<CatalogEvent>> {

    private final Duration askTimeout = Duration.ofSeconds(5);
    private final String topic;
    private final KafkaUtil kafkaUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClusterSharding clusterSharding;

    public CatalogMessageProjectionHandler(ClusterSharding clusterSharding, String topic, KafkaUtil kafkaUtil) {
        this.topic = topic;
        this.kafkaUtil = kafkaUtil;
        this.clusterSharding = clusterSharding;
        objectMapper.registerModule(new DefaultScalaModule());
    }

    @Override
    public CompletionStage<Done> process(R2dbcSession session, EventEnvelope<CatalogEvent> envelope) {
        CatalogEvent event = envelope.event();
        return sendToKafkaTopic(event);
    }
    public EntityRef<CatalogCommand> ref(String id) {
        return clusterSharding.entityRefFor(CatalogAggregate.ENTITY_TYPE_KEY, id);
    }
    public CompletionStage<Optional<Catalog>> getCatalog(EntityRef<CatalogCommand> ref) {
        return ref.<Optional<Catalog>>ask(replyTo -> new CatalogCommand.GetCatalog(replyTo), askTimeout);
    }
    private String toJsonString(Object object){
        if(object == null){
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }
    private CompletionStage<Done> sendToKafkaTopic(CatalogEvent event){
        if (event instanceof CatalogEvent.CatalogCreated) {
            return getCatalog(ref(event.catalogId))
                    .thenApply(optionalCatalog -> {
                        if(optionalCatalog.isPresent()){
                            kafkaUtil.send(topic, event.catalogId, toJsonString(convertToCatalogPublishEvent(event, optionalCatalog.get())));
                            log.info("Catalog message is published to topic::{}, key::{}", topic, event.catalogId);
                        } else {
                            log.warn("Catalog data for id::{} is not found to publish message to topic::{}", event.catalogId, topic);
                        }
                        return Done.getInstance();
                    });
        } else {
            log.debug("event {} is not eligible to send.", event.getClass().getName());
            return CompletableFuture.completedFuture(Done.getInstance());
        }
    }

    public static CatalogMessage convertToCatalogPublishEvent(CatalogEvent event, Catalog catalog) {
        if(event instanceof CatalogEvent.CatalogCreated){
            return new CatalogMessage.CatalogCreatedMessage(convertToCatalogCreatedMessage(catalog));
        } else {
            log.error("Try to convert non publish CatalogEvent: {}", event);
            throw new IllegalArgumentException("non publish CatalogEvent");
        }
    }

    private static CatalogMessage.Catalog convertToCatalogCreatedMessage(Catalog catalog) {
        return new CatalogMessage.Catalog(
                catalog.getCatalogId(),
                catalog.getCatalogId(),
                catalog.getActive(),
                catalog.getDeleted()
        );
    }
}