package com.retisio.arc.aggregate.catalog;

import akka.Done;
import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.retisio.arc.serializer.JsonSerializable;
import lombok.Value;

import java.util.Optional;

public interface CatalogCommand extends JsonSerializable {

    @Value
    @JsonDeserialize
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
        }
    }

    @Value
    @JsonDeserialize
    class GetCatalog implements CatalogCommand {
        ActorRef<Optional<Catalog>> replyTo;

        @JsonCreator
        public GetCatalog(ActorRef<Optional<Catalog>> replyTo) {
            this.replyTo = replyTo;
        }
    }

}
