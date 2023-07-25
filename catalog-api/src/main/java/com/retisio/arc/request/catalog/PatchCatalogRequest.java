package com.retisio.arc.request.catalog;

import lombok.Value;

@Value
public class PatchCatalogRequest {
    private String catalogName;
    private Boolean active;
}
