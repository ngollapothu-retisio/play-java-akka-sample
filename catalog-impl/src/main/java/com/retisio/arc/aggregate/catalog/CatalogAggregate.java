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
