package com.retisio.arc.aggregate.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.retisio.arc.serializer.JsonSerializable;

import java.util.Optional;

@JsonDeserialize
public class CatalogState implements JsonSerializable {

    public static final CatalogState EMPTY = new CatalogState(Optional.empty());

    public final Optional<Catalog> catalog;

    @JsonCreator
    public CatalogState(Optional<Catalog> catalog) {
        this.catalog = catalog;
    }

    public CatalogState createCatalog(CatalogEvent.CatalogCreated event){
        return new CatalogState(
                Optional.of(
                        new Catalog(
                                event.catalogId,
                                event.getCatalogName(),
                                event.getActive(),
                                false
                        )
                )
        );
    }

}
