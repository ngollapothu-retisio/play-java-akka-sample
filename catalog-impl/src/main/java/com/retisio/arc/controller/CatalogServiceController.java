package com.retisio.arc.controller;

import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import com.retisio.arc.request.catalog.CreateCatalogRequest;
import com.retisio.arc.service.CatalogService;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CatalogServiceController extends Controller  {

    @Inject
    private CatalogService catalogService;

    @Inject
    public CatalogServiceController(ActorSystem classicActorSystem){

        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);

        AkkaManagement.get(typedActorSystem).start();
        ClusterBootstrap.get(typedActorSystem).start();

    }

    public CompletionStage<Result> ping() {
        return CompletableFuture.completedFuture(ok("Ok"));
    }

    public CompletionStage<Result> createCatalog(Http.Request request) {
        CreateCatalogRequest createCatalogRequest = Json.fromJson(request.body().asJson(), CreateCatalogRequest.class);
        return catalogService.createCatalog(createCatalogRequest)
                .thenApply(r -> ok(Json.toJson(r)));
    }
}