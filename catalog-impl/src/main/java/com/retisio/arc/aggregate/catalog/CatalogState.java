package com.retisio.arc.aggregate.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.retisio.arc.serializer.JsonSerializable;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@JsonDeserialize
@Slf4j
public class CatalogState implements JsonSerializable {

    public static final CatalogState EMPTY = new CatalogState(Optional.empty());

    public final Optional<Catalog> catalog;

    @JsonCreator
    public CatalogState(Optional<Catalog> catalog) {
        this.catalog = catalog;
    }

    public CatalogState createCatalog(CatalogEvent.CatalogCreated event){
        log.info("CatalogState .... createCatalog::{}", event.catalogId);
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
    public CatalogState updateCatalog(CatalogEvent.CatalogUpdated event){
        log.info("CatalogState .... updateCatalog::{}", event.catalogId);
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
    public CatalogState patchCatalog(CatalogEvent.CatalogPatched event){
        log.info("CatalogState .... patchCatalog::{}", event.catalogId);
        return new CatalogState(
                Optional.of(
                        new Catalog(
                                event.catalogId,
                                Optional.ofNullable(event.getCatalogName()).orElseGet(()->this.catalog.get().getCatalogName()),
                                Optional.ofNullable(event.getActive()).orElseGet(()->this.catalog.get().getActive()),
                                false
                        )
                )
        );
    }
    public CatalogState deleteCatalog(CatalogEvent.CatalogDeleted event){
        log.info("CatalogState .... deleteCatalog::{}", event.catalogId);
        return new CatalogState(
                Optional.of(
                        new Catalog(
                                event.catalogId,
                                this.catalog.get().getCatalogName(),
                                this.catalog.get().getActive(),
                                true
                        )
                )
        );
    }

}
