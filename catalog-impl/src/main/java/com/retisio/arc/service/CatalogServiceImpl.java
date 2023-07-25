package com.retisio.arc.service;

import akka.Done;
import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.retisio.arc.aggregate.catalog.Catalog;
import com.retisio.arc.aggregate.catalog.CatalogAggregate;
import com.retisio.arc.aggregate.catalog.CatalogCommand;
import com.retisio.arc.exception.AlreadyExistsException;
import com.retisio.arc.exception.Error;
import com.retisio.arc.exception.IllegalOperationException;
import com.retisio.arc.exception.NotFoundException;
import com.retisio.arc.execution.ServiceExecutionContext;
import com.retisio.arc.listener.MessageListener;
import com.retisio.arc.listener.handler.BrandMessageHandler;
import com.retisio.arc.projection.catalog.CatalogProjection;
import com.retisio.arc.repository.catalog.CatalogRepository;
import com.retisio.arc.request.catalog.CreateCatalogRequest;
import com.retisio.arc.request.catalog.PatchCatalogRequest;
import com.retisio.arc.request.catalog.UpdateCatalogRequest;
import com.retisio.arc.response.catalog.GetCatalogResponse;
import com.retisio.arc.response.catalog.GetCatalogsResponse;
import com.retisio.arc.util.KafkaUtil;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Slf4j
public class CatalogServiceImpl implements CatalogService {

    private final ClusterSharding clusterSharding;

    @Inject
    private ServiceExecutionContext serviceExecutionContext;

    @Inject
    private CatalogRepository catalogRepository;

    @Inject
    public CatalogServiceImpl(ActorSystem classicActorSystem,
                              BrandMessageHandler brandMessageHandler,
                              KafkaUtil kafkaUtil){
        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);
        this.clusterSharding = ClusterSharding.get(typedActorSystem);

        CatalogAggregate.init(typedActorSystem, 3,35);

        CatalogProjection.init(typedActorSystem, kafkaUtil);

        MessageListener.init(4, typedActorSystem,"brand-events","catalog-brand-group", brandMessageHandler);
    }

    private static final Duration askTimeout = Duration.ofSeconds(10);

    private EntityRef<CatalogCommand> ref(String entityId) {
        return clusterSharding.entityRefFor(CatalogAggregate.ENTITY_TYPE_KEY, entityId);
    }

    private CompletionStage<Optional<Catalog>> getCatalog(EntityRef<CatalogCommand> ref) {
        return ref.ask(CatalogCommand.GetCatalog::new, askTimeout);
    }

    private CompletionStage<Optional<Catalog>> createCatalog(CreateCatalogRequest request, EntityRef<CatalogCommand> ref) {
        return ref.<Done>ask(replyTo -> new CatalogCommand.CreateCatalog(
                            request.getCatalogId(),
                            request.getCatalogName(),
                            request.getActive(),
                            replyTo
                    ), askTimeout)
                .thenCompose(done -> getCatalog(ref));
    }
    private CompletionStage<Optional<Catalog>> updateCatalog(UpdateCatalogRequest request, EntityRef<CatalogCommand> ref) {
        return ref.<Done>ask(replyTo -> new CatalogCommand.UpdateCatalog(
                            request.getCatalogId(),
                            request.getCatalogName(),
                            request.getActive(),
                            replyTo
                    ), askTimeout)
                .thenCompose(done -> getCatalog(ref));
    }
    private CompletionStage<Optional<Catalog>> patchCatalog(String id, PatchCatalogRequest request, EntityRef<CatalogCommand> ref) {
        return ref.<Done>ask(replyTo -> new CatalogCommand.PatchCatalog(
                            id,
                            request.getCatalogName(),
                            request.getActive(),
                            replyTo
                    ), askTimeout)
                .thenCompose(done -> getCatalog(ref));
    }
    private CompletionStage<Optional<Catalog>> deleteCatalog(String id, EntityRef<CatalogCommand> ref) {
        return ref.<Done>ask(replyTo -> new CatalogCommand.DeleteCatalog(
                            id,
                            replyTo
                    ), askTimeout)
                .thenCompose(done -> getCatalog(ref));
    }

    @Override
    public CompletionStage<GetCatalogResponse> createCatalog(CreateCatalogRequest request) {
        return getCatalog(ref(request.getCatalogId()))
                .thenCompose(optionalCatalog -> {
                    if(optionalCatalog.isPresent()){
                        if(optionalCatalog.get().getDeleted().booleanValue() == true){
                            throw new IllegalOperationException(
                                    Arrays.asList(
                                            new Error("ERROR_003", "Create on already deleted catalog "+request.getCatalogId()+" is not allowed.")
                                    )
                            );
                        }
                        throw new AlreadyExistsException(
                                Arrays.asList(
                                        new Error("ERROR_001", "Catalog "+request.getCatalogId()+" is already exists.")
                                )
                        );
                    }
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
                });

    }

    @Override
    public CompletionStage<GetCatalogResponse> updateCatalog(UpdateCatalogRequest request) {
        return updateCatalog(request, ref(request.getCatalogId()))
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
    public CompletionStage<GetCatalogResponse> patchCatalog(PatchCatalogRequest request, String id) {
        return getCatalog(ref(id))
                .thenCompose(optionalCatalog -> {
                    if(!optionalCatalog.isPresent()){
                        throw new NotFoundException(
                                Arrays.asList(
                                        new Error("ERROR_002", "Catalog "+id+" is not found.")
                                )
                        );
                    }
                    return patchCatalog(id, request, ref(id))
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
                });
    }

    @Override
    public CompletionStage<GetCatalogResponse> deleteCatalog(String id) {
        return getCatalog(ref(id))
                .thenCompose(optionalCatalog -> {
                    if(!optionalCatalog.isPresent()){
                        throw new NotFoundException(
                                Arrays.asList(
                                        new Error("ERROR_002", "Catalog "+id+" is not found.")
                                )
                        );
                    } else if(optionalCatalog.get().getDeleted().booleanValue() == true){
                        throw new IllegalOperationException(
                                Arrays.asList(
                                        new Error("ERROR_003", "Delete on catalog "+id+" is not allowed.")
                                )
                        );
                    }
                    return deleteCatalog(id, ref(id))
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
                });
    }

    @Override
    public CompletionStage<GetCatalogsResponse> getCatalogs(Optional<String> filter, Optional<String> limit, Optional<String> offset) {
        return catalogRepository.getCatalogs(filter, limit, offset);
    }

    @Override
    public CompletionStage<GetCatalogResponse> getCatalog(String id) {
        return getCatalog(ref(id))
                .thenCompose(optionalCatalog -> {
                    if(!optionalCatalog.isPresent()){
                        throw new NotFoundException(
                                Arrays.asList(
                                        new Error("ERROR_002", "Catalog "+id+" is not found.")
                                )
                        );
                    }
                    Catalog catalog = optionalCatalog.get();
                    return CompletableFuture.completedFuture(
                            GetCatalogResponse.builder()
                            .catalogId(catalog.getCatalogId())
                            .catalogName(catalog.getCatalogName())
                            .active(catalog.getActive())
                            .deleted(catalog.getDeleted())
                            .build()
                    );
                });
    }

}
