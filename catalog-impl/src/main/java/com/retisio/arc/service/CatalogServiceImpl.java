package com.retisio.arc.service;

import akka.Done;
import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.retisio.arc.aggregate.catalog.Catalog;
import com.retisio.arc.aggregate.catalog.CatalogAggregate;
import com.retisio.arc.aggregate.catalog.CatalogCommand;
import com.retisio.arc.execution.ServiceExecutionContext;
import com.retisio.arc.projection.catalog.CatalogDbProjection;
import com.retisio.arc.projection.catalog.CatalogMessageProjection;
import com.retisio.arc.repository.catalog.CatalogRepository;
import com.retisio.arc.request.catalog.CreateCatalogRequest;
import com.retisio.arc.response.catalog.GetCatalogResponse;
import com.retisio.arc.response.catalog.GetCatalogsResponse;
import com.retisio.arc.util.KafkaUtil;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

@Slf4j
public class CatalogServiceImpl implements CatalogService {

    private final ClusterSharding clusterSharding;

    @Inject
    private ServiceExecutionContext serviceExecutionContext;

    @Inject
    private CatalogRepository catalogRepository;

    @Inject
    public CatalogServiceImpl(ActorSystem classicActorSystem, KafkaUtil kafkaUtil){
        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);
        this.clusterSharding = ClusterSharding.get(typedActorSystem);

        CatalogAggregate.init(typedActorSystem, 3,35);

        CatalogDbProjection.init(typedActorSystem);
        CatalogMessageProjection.init(typedActorSystem, kafkaUtil);
    }

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

    @Override
    public CompletionStage<GetCatalogsResponse> getCatalogs(Optional<String> filter, Optional<String> limit, Optional<String> offset) {
        return catalogRepository.getCatalogs(filter, limit, offset);
    }

}
