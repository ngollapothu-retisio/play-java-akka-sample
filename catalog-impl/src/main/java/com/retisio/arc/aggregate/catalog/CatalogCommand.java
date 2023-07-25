package com.retisio.arc.aggregate.catalog;

import akka.Done;
import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.retisio.arc.serializer.JsonSerializable;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;


public interface CatalogCommand extends JsonSerializable {

    @Value
    @JsonDeserialize
    @Slf4j
    class GetCatalog implements CatalogCommand {
        ActorRef<Optional<Catalog>> replyTo;

        @JsonCreator
        public GetCatalog(ActorRef<Optional<Catalog>> replyTo) {
            this.replyTo = replyTo;
            log.info("GetCatalog ....");
        }
    }

    @Value
    @JsonDeserialize
    @Slf4j
    class CreateCatalog implements CatalogCommand {
        String catalogId;
        String catalogName;
        Boolean active;
        ActorRef<Done> replyTo;

        @JsonCreator
        public CreateCatalog(String catalogId, String catalogName, Boolean active, ActorRef<Done> replyTo) {
            this.catalogId = catalogId;
            this.catalogName = catalogName;
            this.active = active;
            this.replyTo = replyTo;
            log.info("CreateCatalog ....");
        }
    }

    @Value
    @JsonDeserialize
    @Slf4j
    class UpdateCatalog implements CatalogCommand {
        String catalogId;
        String catalogName;
        Boolean active;
        ActorRef<Done> replyTo;

        @JsonCreator
        public UpdateCatalog(String catalogId, String catalogName, Boolean active, ActorRef<Done> replyTo) {
            this.catalogId = catalogId;
            this.catalogName = catalogName;
            this.active = active;
            this.replyTo = replyTo;
            log.info("CreateCatalog ....");
        }
    }

    @Value
    @JsonDeserialize
    @Slf4j
    class PatchCatalog implements CatalogCommand {
        String catalogId;
        String catalogName;
        Boolean active;
        ActorRef<Done> replyTo;

        @JsonCreator
        public PatchCatalog(String catalogId, String catalogName, Boolean active, ActorRef<Done> replyTo) {
            this.catalogId = catalogId;
            this.catalogName = catalogName;
            this.active = active;
            this.replyTo = replyTo;
            log.info("CreateCatalog ....");
        }
    }

    @Value
    @JsonDeserialize
    @Slf4j
    class DeleteCatalog implements CatalogCommand {
        String catalogId;
        ActorRef<Done> replyTo;

        @JsonCreator
        public DeleteCatalog(String catalogId, ActorRef<Done> replyTo) {
            this.catalogId = catalogId;
            this.replyTo = replyTo;
            log.info("CreateCatalog ....");
        }
    }

}
