package com.retisio.arc.controller;

import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import com.retisio.arc.request.catalog.CreateCatalogRequest;
import com.retisio.arc.service.CatalogService;
import lombok.extern.slf4j.Slf4j;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Slf4j
public class CatalogServiceController extends Controller  {

    @Inject
    private CatalogService catalogService;

    @Inject
    public CatalogServiceController(ActorSystem classicActorSystem){


        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);

        AkkaManagement.get(typedActorSystem).start();
        ClusterBootstrap.get(typedActorSystem).start();
        log.info("AkkaManagement & ClusterBootstrap are started ....");
    }

    public CompletionStage<Result> ping() {
        log.info("ping api invoked ....");
        return CompletableFuture.completedFuture(ok("Ok"));
    }

    public CompletionStage<Result> createCatalog(Http.Request request) {
        log.info("createCatalog api invoked ....");
        CreateCatalogRequest createCatalogRequest = Json.fromJson(request.body().asJson(), CreateCatalogRequest.class);
        return catalogService.createCatalog(createCatalogRequest)
                .thenApply(r -> ok(Json.toJson(r)));
    }
}