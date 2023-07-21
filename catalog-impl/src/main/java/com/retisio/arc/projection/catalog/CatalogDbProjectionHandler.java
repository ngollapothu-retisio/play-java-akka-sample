package com.retisio.arc.projection.catalog;

import akka.Done;
import akka.persistence.query.typed.EventEnvelope;
import akka.projection.r2dbc.javadsl.R2dbcHandler;
import akka.projection.r2dbc.javadsl.R2dbcSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.scala.DefaultScalaModule;
import com.retisio.arc.aggregate.catalog.CatalogEvent;
import com.retisio.arc.r2dbc.StatementWrapper;
import lombok.extern.slf4j.Slf4j;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class CatalogDbProjectionHandler extends R2dbcHandler<EventEnvelope<CatalogEvent>> {

    @Override
    public CompletionStage<Done> process(R2dbcSession session, EventEnvelope<CatalogEvent> envelope) {
        CatalogEvent event = envelope.event();
        return processReadSide(session, event);
    }

    private CompletionStage<Done> processReadSide(R2dbcSession session, CatalogEvent event){
        if(event instanceof CatalogEvent.CatalogCreated) {
            return saveCatalog(session, (CatalogEvent.CatalogCreated)event);
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
    private CompletionStage<Done> saveCatalog(R2dbcSession session, CatalogEvent.CatalogCreated event) {
    	log.info("saveCatalog catalogId::{}", event.catalogId);
    	AtomicInteger index = new AtomicInteger(-1);
        StatementWrapper statementWrapper = new StatementWrapper(session.createStatement(SAVE_CATALOG_QUERY));
        statementWrapper.bind(index.incrementAndGet(), event.catalogId, String.class);
        statementWrapper.bind(index.incrementAndGet(), event.getCatalogName(), String.class);
        statementWrapper.bind(index.incrementAndGet(), event.getActive(), Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), false, Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), Timestamp.valueOf(LocalDateTime.now()), Timestamp.class);
        return session.updateOne(statementWrapper.getStatement())
                .thenApply(rowsUpdated -> Done.getInstance());
    }

}