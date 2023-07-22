package com.retisio.arc.service;

import com.retisio.arc.request.catalog.CreateCatalogRequest;
import com.retisio.arc.response.catalog.GetCatalogResponse;
import com.retisio.arc.response.catalog.GetCatalogsResponse;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface CatalogService {
    public CompletionStage<GetCatalogResponse> createCatalog(CreateCatalogRequest request);
    public CompletionStage<GetCatalogsResponse> getCatalogs(Optional<String> filter, Optional<String> limit, Optional<String> offset);
}
