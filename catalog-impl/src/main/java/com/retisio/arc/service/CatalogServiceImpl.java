package com.retisio.arc.service;

import com.retisio.arc.execution.ServiceExecutionContext;
import com.retisio.arc.request.catalog.CreateCatalogRequest;
import com.retisio.arc.response.catalog.GetCatalogResponse;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CatalogServiceImpl implements CatalogService {

    @Inject
    private ServiceExecutionContext serviceExecutionContext;

    @Override
    public CompletionStage<GetCatalogResponse> createCatalog(CreateCatalogRequest request) {
        return CompletableFuture.supplyAsync(()->{
            return GetCatalogResponse.builder()
                    .catalogId(request.getCatalogId())
                    .catalogName(request.getCatalogName())
                    .active(request.getActive())
                    .deleted(false)
                    .build();
        }, serviceExecutionContext);
    }

}
