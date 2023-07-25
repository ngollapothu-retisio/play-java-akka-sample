package com.retisio.arc.controller;

import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import com.retisio.arc.request.catalog.CreateCatalogRequest;
import com.retisio.arc.request.catalog.PatchCatalogRequest;
import com.retisio.arc.request.catalog.UpdateCatalogRequest;
import com.retisio.arc.response.catalog.GetCatalogResponse;
import com.retisio.arc.response.catalog.GetCatalogsResponse;
import com.retisio.arc.service.CatalogService;
import lombok.extern.slf4j.Slf4j;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.inject.Inject;
import java.util.Optional;
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
    }

    public CompletionStage<Result> ping() {
        return CompletableFuture.completedFuture(ok("Ok"));
    }


    public CompletionStage<Result> getCatalogs(Http.Request request) {
        return catalogService.getCatalogs(
                request.queryString("filter"),
                request.queryString("limit"),
                request.queryString("offset")
        )
                .thenApply(r -> ok(Json.toJson(r)));
    }
    public CompletionStage<Result> getCatalog(String id) {
        return catalogService.getCatalog(id)
                .thenApply(r -> ok(Json.toJson(r)));
    }
    public CompletionStage<Result> createCatalog(Http.Request request) {
        CreateCatalogRequest createCatalogRequest = Json.fromJson(request.body().asJson(), CreateCatalogRequest.class);
        return catalogService.createCatalog(createCatalogRequest)
                .thenApply(r -> ok(Json.toJson(r)));
    }
    public CompletionStage<Result> updateCatalog(Http.Request request){
        UpdateCatalogRequest updateCatalogRequest = Json.fromJson(request.body().asJson(), UpdateCatalogRequest.class);
        return catalogService.updateCatalog(updateCatalogRequest)
                .thenApply(r -> ok(Json.toJson(r)));
    }
    public CompletionStage<Result> patchCatalog(Http.Request request, String id){
        PatchCatalogRequest patchCatalogRequest = Json.fromJson(request.body().asJson(), PatchCatalogRequest.class);
        return catalogService.patchCatalog(patchCatalogRequest, id)
                .thenApply(r -> ok(Json.toJson(r)));
    }
    public CompletionStage<Result> deleteCatalog(String id) {
        return catalogService.deleteCatalog(id)
                .thenApply(r -> ok(Json.toJson(r)));
    }
}