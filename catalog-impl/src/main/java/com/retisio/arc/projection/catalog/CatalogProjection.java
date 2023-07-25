package com.retisio.arc.projection.catalog;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.ShardedDaemonProcess;
import akka.japi.Pair;
import akka.persistence.query.Offset;
import akka.persistence.query.typed.EventEnvelope;
import akka.persistence.r2dbc.query.javadsl.R2dbcReadJournal;
import akka.projection.Projection;
import akka.projection.ProjectionBehavior;
import akka.projection.ProjectionId;
import akka.projection.eventsourced.javadsl.EventSourcedProvider;
import akka.projection.javadsl.SourceProvider;
import akka.projection.r2dbc.R2dbcProjectionSettings;
import akka.projection.r2dbc.javadsl.R2dbcProjection;
import com.retisio.arc.aggregate.catalog.CatalogAggregate;
import com.retisio.arc.aggregate.catalog.CatalogEvent;
import com.retisio.arc.util.KafkaUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
public class CatalogProjection {

    public static void init(ActorSystem system, KafkaUtil kafkaUtil) {

        String topic = system.settings().config().getString("catalog.kafka.message.topic");

        // Split the slices into 4 ranges
        int numberOfSliceRanges = 4;
        List<Pair<Integer, Integer>> sliceRanges =
                EventSourcedProvider.sliceRanges(
                        system, R2dbcReadJournal.Identifier(), numberOfSliceRanges);

        ShardedDaemonProcess.get(system)
                .init(
                        ProjectionBehavior.Command.class,
                        "CatalogProjection",
                        sliceRanges.size(),
                        i -> ProjectionBehavior.create(createProjection(system, sliceRanges.get(i), topic, kafkaUtil)),
                        ProjectionBehavior.stopMessage());
    }

    private static Projection<EventEnvelope<CatalogEvent>> createProjection(ActorSystem<?> system,
                                                                            Pair<Integer, Integer> sliceRange,
                                                                            String topic, KafkaUtil kafkaUtil) {
        int minSlice = sliceRange.first();
        int maxSlice = sliceRange.second();

        String entityType = CatalogAggregate.ENTITY_TYPE_KEY.name();

        SourceProvider<Offset, EventEnvelope<CatalogEvent>> sourceProvider =
                EventSourcedProvider.eventsBySlices(
                        system, R2dbcReadJournal.Identifier(), entityType, minSlice, maxSlice);

        ProjectionId projectionId =
                ProjectionId.of("CatalogProjection", "catalog-message-" + minSlice + "-" + maxSlice);
        Optional<R2dbcProjectionSettings> settings = Optional.empty();

        int saveOffsetAfterEnvelopes = 100;
        Duration saveOffsetAfterDuration = Duration.ofMillis(500);
        log.info("CatalogProjection init()..................");
        return R2dbcProjection.atLeastOnce(
                        projectionId, settings, sourceProvider, () -> new CatalogProjectionHandler(ClusterSharding.get(system), topic, kafkaUtil), system)
                        .withSaveOffset(saveOffsetAfterEnvelopes, saveOffsetAfterDuration);
    }
}
