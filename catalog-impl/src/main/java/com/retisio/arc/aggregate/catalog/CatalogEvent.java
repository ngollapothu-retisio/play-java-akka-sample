package com.retisio.arc.aggregate.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.retisio.arc.serializer.JsonSerializable;
import lombok.Value;

public abstract class CatalogEvent implements JsonSerializable {

    final public String catalogId;

    public CatalogEvent(String catalogId){
        this.catalogId = catalogId;
    }

    @Value
    @JsonDeserialize
    final static class CatalogCreated extends CatalogEvent {

        String catalogName;
        Boolean active;

        @JsonCreator
        private CatalogCreated(String catalogId, String catalogName, Boolean active) {
            super(catalogId);
            this.catalogName = catalogName;
            this.active = active;
        }

        static CatalogCreated getInstance(CatalogCommand.CreateCatalog cmd) {
            return new CatalogCreated(
                    cmd.getCatalogId(),
                    cmd.getCatalogName(),
                    cmd.getActive()
            );
        }
    }

}
