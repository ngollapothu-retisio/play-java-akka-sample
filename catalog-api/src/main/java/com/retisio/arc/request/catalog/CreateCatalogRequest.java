package com.retisio.arc.request.catalog;

import lombok.Value;

@Value
public class CreateCatalogRequest {
    private String catalogId;
    private String catalogName;
    private Boolean active;
}
