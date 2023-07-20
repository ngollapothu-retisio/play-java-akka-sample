# play-java-akka-sample
play-java-akka-sample

Steps to run application

Step 1: clone the code
```
git clone https://github.com/ngollapothu-retisio/play-java-akka-sample.git
git checkout 0.1-akka-management-enabled
git pull
```

Step 2: run the service in local
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

1. Enabled Akka management & Custer bootstrap
```
    @Inject
    public CatalogServiceController(ActorSystem classicActorSystem){

        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);

        AkkaManagement.get(typedActorSystem).start();
        ClusterBootstrap.get(typedActorSystem).start();

    }
```
2. Add custom thread pool

file: application.conf
```
service.execution.dispatcher {
    executor = "thread-pool-executor"
    throughtput = 2
    thread-pool-executor {
        fixed-pool-size = 2
    }
}
```
```
import akka.actor.ActorSystem;
import play.libs.concurrent.CustomExecutionContext;

import javax.inject.Inject;

public class ServiceExecutionContext extends CustomExecutionContext {

    @Inject
    public ServiceExecutionContext(ActorSystem actorSystem) {
        super(actorSystem, "service.execution.dispatcher");
    }

}
```
3. execute service bussiness logic on custom thread pool
```
public class CatalogServiceImpl implements CatalogService {

    @Inject
    private ServiceExecutionContext serviceExecutionContext;

    @Override
    public CompletionStage<GetCatalogResponse> createCatalog(CreateCatalogRequest request) {
        return CompletableFuture.supplyAsync(()->{
            return GetCatalogResponse.builder()
                    //...
                    .build();
        }, serviceExecutionContext);
    }

}
```
4. Dependency injection using guice library
```
public class CatalogModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CatalogService.class).to(CatalogServiceImpl.class).asEagerSingleton();
        bind(ServiceExecutionContext.class).asEagerSingleton();
    }

}

```

file: application.conf
```
play.modules.enabled += com.retisio.arc.module.CatalogModule
```

5. config Play routers for Service APIs

file: conf/routes
```
# Routes
# This file defines all application routes (Higher priority routes first)
# ~~~~
GET     /                  com.retisio.arc.controller.CatalogServiceController.ping
POST    /catalogs          com.retisio.arc.controller.CatalogServiceController.createCatalog(request:Request)
```

```
public class CatalogServiceController extends Controller  {

    @Inject
    private CatalogService catalogService;

    //.....
    //contructor with akka management & cluster bootstrap starts
    //.....

    public CompletionStage<Result> ping() {
        return CompletableFuture.completedFuture(ok("Ok"));
    }

    public CompletionStage<Result> createCatalog(Http.Request request) {
        CreateCatalogRequest createCatalogRequest = Json.fromJson(request.body().asJson(), CreateCatalogRequest.class);
        return catalogService.createCatalog(createCatalogRequest)
                .thenApply(r -> ok(Json.toJson(r)));
    }
}
```

