package com.retisio.arc.repository.catalog;

import com.retisio.arc.r2dbc.R2dbcConnectionFactroyWrapper;
import com.retisio.arc.r2dbc.StatementWrapper;
import com.retisio.arc.response.catalog.GetCatalogResponse;
import com.retisio.arc.response.catalog.GetCatalogsResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;
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

    public CompletionStage<GetCatalogsResponse> getCatalogs(Optional<String> filter, Optional<String> limit, Optional<String> offset) {
        return Mono.usingWhen(connectionFactoryWrapper.connectionFactory().create(),
                connection -> {
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
                    String pageSize = limit.orElseGet(()->"10");
                    String offSetValue = offset.orElseGet(()->"0");
                    String query = "select catalog_id, catalog_name, is_active, is_deleted, COUNT(1) OVER () as TOTAL_COUNT from catalog " +
                            filterQuery + " OFFSET "+offSetValue+" LIMIT "+pageSize;

                    AtomicInteger searchTotalCount = new AtomicInteger();
                    log.info("query::{}", query);
                    StatementWrapper statementWrapper = new StatementWrapper(connection.createStatement(query));
                    return Flux.from(statementWrapper.getStatement().execute())
                            .flatMap(result -> result.map(row -> {
                                searchTotalCount.set(row.get("TOTAL_COUNT", Integer.class));
                                return GetCatalogResponse.builder()
                                        .catalogId(row.get("catalog_id", String.class))
                                        .catalogName(row.get("catalog_name", String.class))
                                        .active(row.get("is_active", Boolean.class))
                                        .deleted(row.get("is_deleted", Boolean.class))
                                        .build();
                            }))
                            .collectList()
                            .map(list -> Optional.ofNullable(list))
                            .switchIfEmpty(Mono.just(Optional.empty()))
                            .map(optList -> {
                                List<GetCatalogResponse> list = new ArrayList<>();
                                if(optList.isPresent()){
                                    list.addAll(optList.get());
                                }
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
                            });
                },
                connection -> connection.close()).toFuture();
    }

}
