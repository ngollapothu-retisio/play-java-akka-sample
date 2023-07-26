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
import com.retisio.arc.r2dbc.StatementWrapper;
import com.retisio.arc.util.KafkaUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CatalogProjectionHandler extends R2dbcHandler<EventEnvelope<CatalogEvent>> {

    private final Duration askTimeout = Duration.ofSeconds(5);
    private final String topic;
    private final KafkaUtil kafkaUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClusterSharding clusterSharding;

    public CatalogProjectionHandler(ClusterSharding clusterSharding, String topic, KafkaUtil kafkaUtil) {
        this.topic = topic;
        this.kafkaUtil = kafkaUtil;
        this.clusterSharding = clusterSharding;
        objectMapper.registerModule(new DefaultScalaModule());
    }

    @Override
    public CompletionStage<Done> process(R2dbcSession session, EventEnvelope<CatalogEvent> envelope) {
        CatalogEvent event = envelope.event();
        return processReadSide(session, event)
                .thenCompose(done -> sendToKafkaTopic(event));
    }
    private CompletionStage<Done> processReadSide(R2dbcSession session, CatalogEvent event){
        if(event instanceof CatalogEvent.CatalogCreated) {
            return insertCatalog(session, (CatalogEvent.CatalogCreated)event);
        }else if(event instanceof CatalogEvent.CatalogUpdated) {
            return updateCatalog(session, (CatalogEvent.CatalogUpdated)event);
        }else if(event instanceof CatalogEvent.CatalogPatched) {
            return patchCatalog(session, (CatalogEvent.CatalogPatched)event);
        }else if(event instanceof CatalogEvent.CatalogDeleted) {
            return deleteCatalog(session, (CatalogEvent.CatalogDeleted)event);
        }else {
            return CompletableFuture.completedFuture(Done.getInstance());
        }
    }
    private static final String SAVE_CATALOG_QUERY = "INSERT INTO CATALOG(" +
            "CATALOG_ID, " +
            "CATALOG_NAME, " +
            "IS_ACTIVE, " +
            "IS_DELETED, " +
            "LAST_MODIFIED_TMST) " +
            "VALUES ($1, $2, $3, $4, $5) " +
            "on conflict (CATALOG_ID) DO UPDATE set " +
            "CATALOG_ID=excluded.CATALOG_ID, " +
            "CATALOG_NAME=excluded.CATALOG_NAME, " +
            "IS_ACTIVE=excluded.IS_ACTIVE, " +
            "IS_DELETED=excluded.IS_DELETED, " +
            "LAST_MODIFIED_TMST=now()";
    private CompletionStage<Done> insertCatalog(R2dbcSession session, CatalogEvent.CatalogCreated event) {
        log.info("insertCatalog catalogId::{}", event.catalogId);
        AtomicInteger index = new AtomicInteger(-1);
        String query = SAVE_CATALOG_QUERY;
        log.info("query::{}", query);
        StatementWrapper statementWrapper = new StatementWrapper(session.createStatement(query));
        statementWrapper.bind(index.incrementAndGet(), event.catalogId, String.class);
        statementWrapper.bind(index.incrementAndGet(), event.getCatalogName(), String.class);
        statementWrapper.bind(index.incrementAndGet(), event.getActive(), Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), false, Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), Timestamp.valueOf(LocalDateTime.now()), Timestamp.class);
        return session.updateOne(statementWrapper.getStatement())
                .thenApply(rowsUpdated -> Done.getInstance());
    }
    private CompletionStage<Done> updateCatalog(R2dbcSession session, CatalogEvent.CatalogUpdated event) {
        log.info("updateCatalog catalogId::{}", event.catalogId);
        AtomicInteger index = new AtomicInteger(-1);
        String query = SAVE_CATALOG_QUERY;
        log.info("query::{}", query);
        StatementWrapper statementWrapper = new StatementWrapper(session.createStatement(query));
        statementWrapper.bind(index.incrementAndGet(), event.catalogId, String.class);
        statementWrapper.bind(index.incrementAndGet(), event.getCatalogName(), String.class);
        statementWrapper.bind(index.incrementAndGet(), event.getActive(), Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), false, Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), Timestamp.valueOf(LocalDateTime.now()), Timestamp.class);
        return session.updateOne(statementWrapper.getStatement())
                .thenApply(rowsUpdated -> Done.getInstance());
    }
    private CompletionStage<Done> patchCatalog(R2dbcSession session, CatalogEvent.CatalogPatched event) {
        log.info("patchCatalog catalogId::{}", event.catalogId);
        AtomicInteger index = new AtomicInteger(-1);
        AtomicInteger q_index = new AtomicInteger(0);
        StringBuilder query = new StringBuilder("update CATALOG set ");
        if(Objects.nonNull(event.getCatalogName())) {
            query.append("CATALOG_NAME = $"+q_index.incrementAndGet()+", ");
        }
        if(Objects.nonNull(event.getActive())) {
            query.append("is_active = $"+q_index.incrementAndGet()+", ");
        }
        query.append("LAST_MODIFIED_TMST=now() where catalog_id = $"+q_index.incrementAndGet());
        log.info("query::{}", query.toString());
        StatementWrapper statementWrapper = new StatementWrapper(session.createStatement(query.toString()));
        if(Objects.nonNull(event.getCatalogName())) {
            statementWrapper.bind(index.incrementAndGet(), event.getCatalogName(), String.class);
        }
        if(Objects.nonNull(event.getActive())) {
            statementWrapper.bind(index.incrementAndGet(), event.getActive(), Boolean.class);
        }
        statementWrapper.bind(index.incrementAndGet(), event.catalogId, String.class);
        return session.updateOne(statementWrapper.getStatement())
                .thenApply(rowsUpdated -> Done.getInstance());
    }
    private CompletionStage<Done> deleteCatalog(R2dbcSession session, CatalogEvent.CatalogDeleted event) {
        log.info("deleteCatalog catalogId::{}", event.catalogId);
        AtomicInteger index = new AtomicInteger(-1);
        String query = "update CATALOG set is_deleted = true, LAST_MODIFIED_TMST=now() where catalog_id = $1";
        log.info("query::{}", query);
        StatementWrapper statementWrapper = new StatementWrapper(session.createStatement(query));
        statementWrapper.bind(index.incrementAndGet(), event.catalogId, String.class);
        return session.updateOne(statementWrapper.getStatement())
                .thenApply(rowsUpdated -> Done.getInstance());
    }

    public EntityRef<CatalogCommand> ref(String id) {
        return clusterSharding.entityRefFor(CatalogAggregate.ENTITY_TYPE_KEY, id);
    }
    public CompletionStage<Optional<Catalog>> getCatalog(EntityRef<CatalogCommand> ref) {
        return ref.<Optional<Catalog>>ask(replyTo -> new CatalogCommand.GetCatalog(ref.getEntityId(), replyTo), askTimeout);
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
        if (event instanceof CatalogEvent.CatalogCreated
            || event instanceof CatalogEvent.CatalogUpdated
            || event instanceof CatalogEvent.CatalogPatched
            || event instanceof CatalogEvent.CatalogDeleted
        ) {
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
            return new CatalogMessage.CatalogCreatedMessage(convertToCatalogMessage(catalog));
        } else if(event instanceof CatalogEvent.CatalogUpdated){
            return new CatalogMessage.CatalogUpdatedMessage(convertToCatalogMessage(catalog));
        } else if(event instanceof CatalogEvent.CatalogPatched){
            return new CatalogMessage.CatalogPatchedMessage(convertToCatalogMessage(catalog));
        } else if(event instanceof CatalogEvent.CatalogDeleted){
            return new CatalogMessage.CatalogDeletedMessage(convertToCatalogMessage(catalog));
        } else {
            log.error("Try to convert non publish CatalogEvent: {}", event);
            throw new IllegalArgumentException("non publish CatalogEvent");
        }
    }

    private static CatalogMessage.Catalog convertToCatalogMessage(Catalog catalog) {
        return new CatalogMessage.Catalog(
                catalog.getCatalogId(),
                catalog.getCatalogName(),
                catalog.getActive(),
                catalog.getDeleted()
        );
    }
}