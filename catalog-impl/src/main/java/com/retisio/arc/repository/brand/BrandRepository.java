package com.retisio.arc.repository.brand;

import akka.Done;
import com.retisio.arc.message.brand.BrandMessage;
import com.retisio.arc.r2dbc.R2dbcConnectionFactroyWrapper;
import com.retisio.arc.r2dbc.StatementWrapper;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.inject.Inject;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class BrandRepository {

    @Inject
    private R2dbcConnectionFactroyWrapper connectionFactoryWrapper;

    public CompletionStage<Done> saveBrand(BrandMessage.Brand brand) {
        return Mono.usingWhen(connectionFactoryWrapper.connectionFactory().create(),
                connection -> {
                    return saveBrand(connection, brand)
                            .map(result -> Done.getInstance());
                },
                connection -> connection.close()).toFuture();
    }

    private static final String SAVE_BRAND_QUERY = "INSERT INTO BRAND(" +
            "BRAND_ID, " +
            "BRAND_NAME, " +
            "IS_ACTIVE, " +
            "IS_DELETED, " +
            "LAST_MODIFIED_TMST) " +
            "VALUES ($1, $2, $3, $4, $5) " +
            "on conflict (BRAND_ID) DO UPDATE set " +
            "BRAND_ID=excluded.BRAND_ID, " +
            "BRAND_NAME=excluded.BRAND_NAME, " +
            "IS_ACTIVE=excluded.IS_ACTIVE, " +
            "IS_DELETED=excluded.IS_DELETED, " +
            "LAST_MODIFIED_TMST=now()";

    private Mono<List<Integer>> saveBrand(Connection connection, BrandMessage.Brand brand) {
        log.info("saveBrand brandId::{}", brand.getBrandId());
        AtomicInteger index = new AtomicInteger(-1);
        StatementWrapper statementWrapper = new StatementWrapper(connection.createStatement(SAVE_BRAND_QUERY));
        statementWrapper.bind(index.incrementAndGet(), brand.getBrandId(), String.class);
        statementWrapper.bind(index.incrementAndGet(), brand.getBrandName(), String.class);
        statementWrapper.bind(index.incrementAndGet(), Optional.ofNullable(brand.getActive()).orElseGet(()->false), Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), Optional.ofNullable(brand.getDeleted()).orElseGet(()->false), Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), Timestamp.valueOf(LocalDateTime.now()), Timestamp.class);
        return Flux.from(statementWrapper.getStatement().execute())
                .flatMap(Result::getRowsUpdated)
                .onErrorStop()
                .collectList();
    }

}
