package com.retisio.arc.message.catalog;

import com.fasterxml.jackson.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "event", defaultImpl = Void.class)
@JsonSubTypes({
        @JsonSubTypes.Type(CatalogMessage.CatalogCreatedMessage.class),
        @JsonSubTypes.Type(CatalogMessage.CatalogUpdatedMessage.class),
        @JsonSubTypes.Type(CatalogMessage.CatalogPatchedMessage.class),
        @JsonSubTypes.Type(CatalogMessage.CatalogDeletedMessage.class),
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

    @JsonTypeName(value = "catalog-updated")
    @Value
    public final static class CatalogUpdatedMessage extends CatalogMessage {

        public final CatalogMessage.Catalog catalog;

        public CatalogUpdatedMessage(CatalogMessage.Catalog catalog) {
            super(catalog.getCatalogId());
            this.catalog = catalog;
        }
    }
    @JsonTypeName(value = "catalog-patched")
    @Value
    public final static class CatalogPatchedMessage extends CatalogMessage {

        public final CatalogMessage.Catalog catalog;

        public CatalogPatchedMessage(CatalogMessage.Catalog catalog) {
            super(catalog.getCatalogId());
            this.catalog = catalog;
        }
    }
    @JsonTypeName(value = "catalog-deleted")
    @Value
    public final static class CatalogDeletedMessage extends CatalogMessage {

        public final CatalogMessage.Catalog catalog;

        public CatalogDeletedMessage(CatalogMessage.Catalog catalog) {
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
