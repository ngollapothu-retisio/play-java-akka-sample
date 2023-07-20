# play-java-akka-sample
play-java-akka-sample

Steps to run application

Step 1: clone the code
```
git clone https://github.com/ngollapothu-retisio/play-java-akka-sample.git
git checkout 0.2-akka-persistence
git pull
```

Step 2: create catalog_write_side, catalog_read_side databases
```
create database catalog_write_side;
create database catalog_read_side;
```
user: postgres, pwd: postgres

Step 3: execute DDL scripts on respective databases at bash command prompt
```
$ ./migrate-read-db.sh postgres postgres localhost:5432
$ ./migrate-write-db.sh postgres postgres localhost:5432
```

Step 4: run the service in local
```
sbt -Dconfig.resource=application-local.conf run
```

GET Ping the service
```
curl --location --request GET 'http://localhost:9000'
```

POST catalog creation service
```
curl --location --request POST 'http://localhost:9000/catalogs' \
--header 'Content-Type: application/json' \
--data '{
    "catalogId" : "cat-001",
    "catalogName" : "Allen Brothers",
    "active" : true
}'
```

About the code

Before jumping to the Akka persistence code changes, please check the below link
https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.1-akka-management-enabled/README.md

New files are added in this branch
```
1. Catalog
   CatalogAggregate
   CatalogCommand
   CatalogEvent
   CatalogState
2. JsonSerializable
3. r2dbc-write-side.conf
   serialization.conf
4. sql/catalog_write_side/V1__Intial_r2dbc_write_1.sql
   sql/catalog_read_side/V1__Intial_r2dbc_read_1.sql
```

**Details:**

1. Akka persitence DDL scripts are available in sql/catalog_write_side/V1__Intial_r2dbc_write_1.sql. These tables are different from legacy lagom persistence DDL structure.
```
$ ./migrate-write-db.sh postgres postgres localhost:5432
```
2. Akka projection DDL scripts are available in sql/catalog_read_side/V1__Intial_r2dbc_read_1.sql. These tables are different from legacy lagom read side DDL structure.
```
$ ./migrate-read-db.sh postgres postgres localhost:5432
```
3. similar legacy lagom persistence, Akka persistence also does the actor operations through Aggregate class. Command, Event, State classes are considered as messages 
and flowing over the networks and Event, State messages are persisted in event_journal, event_snapshot tables in write side database catalog_write_database.

go through the classes for best practices
```
   Catalog
   CatalogAggregate
   CatalogCommand
   CatalogEvent
   CatalogState
```
3. Catalog, CatalogCommand, CatalogEvent, and CatalogState classes are marked with JsonSerializable interface, and annotated with @JsonDeserialize, @JsonCreator

file: JsonSerializable.java
```
package com.retisio.arc.serializer;

public interface JsonSerializable {}
```

file: Catalog.java
```
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize
public class Catalog implements JsonSerializable {
    private String catalogId;
    private String catalogName;
    private Boolean active;
    private Boolean deleted;
}
```
file: CatalogCommand.java
```
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
```
file: CatalogEvent.java
```
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
```
file: CatalogState.java
```
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
```
file: CatalogAggregate.java
```
package com.retisio.arc.aggregate.catalog;

import akka.Done;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;

public class CatalogAggregate extends EventSourcedBehaviorWithEnforcedReplies<CatalogCommand, CatalogEvent, CatalogState> {

    //----------------------------
    public static EntityTypeKey<CatalogCommand> ENTITY_TYPE_KEY = EntityTypeKey.create(CatalogCommand.class, "CatalogAggregate");

    static Integer numberOfEvents;
    static Integer keepNSnapshots;

    public static void init(ClusterSharding clusterSharding, Integer numberOfEvents, Integer keepNSnapshots) {
        clusterSharding.init(
                Entity.of(
                        ENTITY_TYPE_KEY,
                        entityContext -> {
                            return CatalogAggregate.create(entityContext.getEntityId(), numberOfEvents, keepNSnapshots);
                        }));
    }

    public static Behavior<CatalogCommand> create(String entityId, Integer numberOfEvents, Integer keepNSnapshots) {
        return Behaviors.setup(
                ctx -> EventSourcedBehavior.start(new CatalogAggregate(entityId, numberOfEvents, keepNSnapshots), ctx));
    }

    private CatalogAggregate(String entityId, Integer numberOfEvents, Integer keepNSnapshots) {
        super(
                PersistenceId.of(ENTITY_TYPE_KEY.name(), entityId)
        );
        this.numberOfEvents = numberOfEvents;
        this.keepNSnapshots = keepNSnapshots;
    }
    //----------------------------------
    @Override
    public RetentionCriteria retentionCriteria() {
        return RetentionCriteria.snapshotEvery(numberOfEvents,
                keepNSnapshots).withDeleteEventsOnSnapshot();
    }
    //---------------------------------

    @Override
    public CatalogState emptyState() {
        return CatalogState.EMPTY;
    }

    @Override
    public CommandHandlerWithReply<CatalogCommand, CatalogEvent, CatalogState> commandHandler() {
        return newCommandHandlerWithReplyBuilder()
                .forAnyState()
                .onCommand(CatalogCommand.GetCatalog.class, (state, cmd) -> Effect()
                        .none()
                        .thenReply(cmd.getReplyTo(), __ -> state.catalog))
                .onCommand(CatalogCommand.CreateCatalog.class, (state, cmd) -> Effect()
                        .persist(CatalogEvent.CatalogCreated.getInstance(cmd))
                        .thenReply(cmd.getReplyTo(), __ -> Done.getInstance()))
                .build();
    }

    @Override
    public EventHandler<CatalogState, CatalogEvent> eventHandler() {
        return newEventHandlerBuilder().
                forAnyState()
                .onEvent(CatalogEvent.CatalogCreated.class,
                        (state, evt) -> state.createCatalog(evt))
                .build();
    }
    //-------------------------------
}

```
4. register the CatalogAggregate
file: CatalogServiceImpl.java 
```
    @Inject
    public CatalogServiceImpl(ActorSystem classicActorSystem){
        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);
        this.clusterSharding = ClusterSharding.get(typedActorSystem);

        CatalogAggregate.init(clusterSharding,3,35);
    }
```
5. we can issue commands using actor reference when APIs are invoked and serve actor state
file: CatalogServiceImpl.java
```
    private static final Duration askTimeout = Duration.ofSeconds(10);

    public EntityRef<CatalogCommand> ref(String entityId) {
        return clusterSharding.entityRefFor(CatalogAggregate.ENTITY_TYPE_KEY, entityId);
    }

    public CompletionStage<Optional<Catalog>> getCatalog(EntityRef<CatalogCommand> ref) {
        return ref.ask(CatalogCommand.GetCatalog::new, askTimeout);
    }

    public CompletionStage<Optional<Catalog>> createCatalog(CreateCatalogRequest request, EntityRef<CatalogCommand> ref) {
        return ref.<Done>ask(replyTo -> new CatalogCommand.CreateCatalog(
                            request.getCatalogId(),
                            request.getCatalogName(),
                            request.getActive(),
                            replyTo
                    ), askTimeout)
                .thenCompose(done -> getCatalog(ref));
    }

    @Override
    public CompletionStage<GetCatalogResponse> createCatalog(CreateCatalogRequest request) {
        return createCatalog(request, ref(request.getCatalogId()))
                .thenApply(optCatalog -> {
                    if(optCatalog.isPresent()){
                        Catalog catalog = optCatalog.get();
                        return GetCatalogResponse.builder()
                                .catalogId(catalog.getCatalogId())
                                .catalogName(catalog.getCatalogName())
                                .active(catalog.getActive())
                                .deleted(catalog.getDeleted())
                                .build();
                    }
                    return GetCatalogResponse.builder().build();
                });
    }
```
6. To enabled Akka persistence include these files in application.conf
```
include "r2dbc-write-side"
include "serialization"
```
