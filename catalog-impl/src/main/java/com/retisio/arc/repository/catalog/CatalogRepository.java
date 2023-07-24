package com.retisio.arc.repository.catalog;

import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.projection.r2dbc.javadsl.R2dbcSession;
import com.retisio.arc.r2dbc.R2dbcConnectionFactroyWrapper;
import com.retisio.arc.r2dbc.StatementWrapper;
import com.retisio.arc.response.catalog.GetCatalogResponse;
import com.retisio.arc.response.catalog.GetCatalogsResponse;
import io.r2dbc.spi.Connection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class CatalogRepository {

    private Map<String, String> columnMap = new HashMap<String, String>() {
        {
            put("catalogId", "catalog_id");
            put("catalogName", "catalog_name");
            put("active", "is_active");
            put("deleted", "is_deleted");
        }
    };

    private List<String> booleanProperties = Arrays.asList("active", "deleted");

    @Inject
    private R2dbcConnectionFactroyWrapper connectionFactoryWrapper;

    private final akka.actor.typed.ActorSystem<Void> typedActorSystem;

    @Inject
    public CatalogRepository(ActorSystem classicActorSystem){
        typedActorSystem = Adapter.toTyped(classicActorSystem);
    }

    private R2dbcSession getR2dbcSession(Connection connection){
        return new R2dbcSession(connection, typedActorSystem.executionContext(), typedActorSystem);
    }

    public CompletionStage<GetCatalogsResponse> getCatalogs(Optional<String> filter, Optional<String> limit, Optional<String> offset) {
        return Mono.usingWhen(
                connectionFactoryWrapper.connectionFactory().create(),
                connection -> getCatalogs(getR2dbcSession(connection), filter, limit, offset),
                connection -> connection.close()
        ).toFuture();
    }

    private String getCatalogsQuery(Optional<String> filter, String pageSize, String offSetValue) {
        String filterQuery = filter
                .map(f -> {
                    log.info("filter::{}", f);
                    return Arrays.asList(f.split(",")).stream()
                            .map(c -> Arrays.asList(c.split("::")))
                            .filter(m -> m.size() == 2)
                            .map(m -> {
                                String columnName = columnMap.get(m.get(0));
                                boolean isBool = booleanProperties.contains(m.get(0));
                                return columnName+ "=" +(isBool?m.get(1):("'"+m.get(1)+"'"));
                            })
                            .collect(Collectors.joining(" and "));
                })
                .filter(StringUtils::isNotBlank)
                .map(c -> "where "+c)
                .orElseGet(()->"");

        return "select catalog_id, catalog_name, is_active, is_deleted, COUNT(1) OVER () as TOTAL_COUNT from catalog " +
                filterQuery + " OFFSET "+offSetValue+" LIMIT "+pageSize;
    }

    private Mono<GetCatalogsResponse> getCatalogs(R2dbcSession r2dbcSession, Optional<String> filter, Optional<String> limit, Optional<String> offset) {
        String pageSize = limit.orElseGet(()->"10");
        String offSetValue = offset.orElseGet(()->"0");
        String query = getCatalogsQuery(filter, pageSize, offSetValue);

        log.info("query::{}", query);
        AtomicInteger searchTotalCount = new AtomicInteger();
        StatementWrapper statementWrapper = new StatementWrapper(r2dbcSession.createStatement(query));
        return Mono.fromCompletionStage(
                r2dbcSession.select(
                        statementWrapper.getStatement(),
                        row -> {
                            searchTotalCount.set(row.get("TOTAL_COUNT", Integer.class));
                            return GetCatalogResponse.builder()
                                    .catalogId(row.get("catalog_id", String.class))
                                    .catalogName(row.get("catalog_name", String.class))
                                    .active(row.get("is_active", Boolean.class))
                                    .deleted(row.get("is_deleted", Boolean.class))
                                    .build();
                        })
                        .thenApply(list -> {
                            GetCatalogsResponse.Pagination pagination =
                                    new GetCatalogsResponse.Pagination(
                                            searchTotalCount.get(),
                                            Integer.parseInt(pageSize),
                                            Integer.parseInt(offSetValue)
                                    );
                            return GetCatalogsResponse.builder()
                                    .pagination(pagination)
                                    .catalogs(list)
                                    .build();
                        })
        );
    }

}
