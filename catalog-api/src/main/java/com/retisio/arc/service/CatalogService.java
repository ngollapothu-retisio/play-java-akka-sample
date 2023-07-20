package com.retisio.arc.service;

import com.retisio.arc.request.catalog.CreateCatalogRequest;
import com.retisio.arc.response.catalog.GetCatalogResponse;

import java.util.concurrent.CompletionStage;

public interface CatalogService {
    public CompletionStage<GetCatalogResponse> createCatalog(CreateCatalogRequest request);
}
