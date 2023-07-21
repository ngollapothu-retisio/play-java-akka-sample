package com.retisio.arc.message.catalog;

import com.fasterxml.jackson.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "event", defaultImpl = Void.class)
@JsonSubTypes({
        @JsonSubTypes.Type(CatalogMessage.CatalogCreatedMessage.class),
})
public abstract class CatalogMessage {

    public final String catalogId;

    public CatalogMessage(String catalogId){
        this.catalogId = catalogId;
    }

    @JsonTypeName(value = "catalog-created")
    @Value
    public final static class CatalogCreatedMessage extends CatalogMessage {

        public final CatalogMessage.Catalog catalog;

        public CatalogCreatedMessage(CatalogMessage.Catalog catalog) {
            super(catalog.getCatalogId());
            this.catalog = catalog;
        }
    }

    @Data
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Catalog {
        private String catalogId;
        private String catalogName;
        private Boolean active;
        private Boolean deleted;
    }
}
