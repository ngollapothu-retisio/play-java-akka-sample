# play-java-akka-sample

Steps to run application

Step 1: clone the code
```
git clone https://github.com/ngollapothu-retisio/play-java-akka-sample.git
git checkout 0.5-akka-r2dbc-repository
git pull
```
Step 2: run the service in local
```
sbt -Dconfig.resource=application-local.conf run
```

Step 3: 

GET catalogs using filters
```
curl --location --request GET 'http://localhost:9000/catalogs?filter=active%3A%3Atrue&limit=10&offset=0'
```
Response:
```
{
    "pagination": {
        "totalCount": 1,
        "limit": 10,
        "offset": 0
    },
    "catalogs": [
        {
            "catalogId": "cat-001",
            "catalogName": "Electronics",
            "active": true,
            "deleted": false
        }
    ]
}
```
Resulted values are fetched from read side database without ES-CQRS, directly from read side table using R2DBC Repository.

About the code

Please check the below links

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.1-akka-management-enabled/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.2-akka-persistence/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.3-akka-db-projection/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.4-akka-message-projection/README.md

New files are added in this branch
```
1. CatalogRepository.java
   R2dbcConnectionFactroyWrapper.java
   GetCatalogsResponse

```

**Details:**

1. Created a new REST Api to get list of catalog details for given filter criteria
```
curl --location --request GET 'http://localhost:9000/catalogs?filter=active%3A%3Atrue&limit=10&offset=0'
```
file: conf/routes
```
GET     /catalogs          com.retisio.arc.controller.CatalogServiceController.getCatalogs(request:Request)
```
file: CatalogServiceController.java
```
    public CompletionStage<Result> getCatalogs(Http.Request request) {
        return catalogService.getCatalogs(
                request.queryString("filter"),
                request.queryString("limit"),
                request.queryString("offset")
        )
                .thenApply(r -> ok(Json.toJson(r)));
    }
```

2. Get Catalogs Api added in CatalogService interface
file: CatalogService.java
```
    public CompletionStage<GetCatalogsResponse> getCatalogs(Optional<String> filter, Optional<String> limit, Optional<String> offset);
```
implementation is provided in CatalogServiceImpl class 

file: CatalogServiceImpl.java
```
    @Inject
    private CatalogRepository catalogRepository;
    
    //.....

    @Override
    public CompletionStage<GetCatalogsResponse> getCatalogs(Optional<String> filter, Optional<String> limit, Optional<String> offset) {
        return catalogRepository.getCatalogs(filter, limit, offset);
    }

```
3. We need a db connection to execute sql queries in r2dbc as well. Below wrapper class is created to service connection objects from r2dbc connection pool.

file: R2dbcConnectionFactroyWrapper.java
```
@Slf4j
@Singleton
public class R2dbcConnectionFactroyWrapper {

    private final ConnectionFactory connectionFactory;

    @Inject
    public R2dbcConnectionFactroyWrapper(ActorSystem classicActorSystem){
        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);
        this.connectionFactory = ConnectionFactoryProvider.get(typedActorSystem)
                .connectionFactoryFor("akka.projection.r2dbc.connection-factory");
    }

    public ConnectionFactory connectionFactory(){
        return this.connectionFactory;
    }
}
```

4. Repository class where we create all CRUD methods which are not relevant to ES-CQRS.

file: CatalogRepository.java
```

    @Inject
    private R2dbcConnectionFactroyWrapper connectionFactoryWrapper;

    public CompletionStage<GetCatalogsResponse> getCatalogs(Optional<String> filter, Optional<String> limit, Optional<String> offset) {
        return Mono.usingWhen(connectionFactoryWrapper.connectionFactory().create(),
                connection -> {
                    //...
                    StatementWrapper statementWrapper = new StatementWrapper(connection.createStatement(query));
                    return Flux.from(statementWrapper.getStatement().execute())
                            .flatMap(result -> result.map(row -> {
                            //..
                           });
                },
                connection -> connection.close()).toFuture();
    }
``` 
In above code snipped shows abstracted details of how db connection is pulled from connection pool factory, statement is executed, connection is released.

 - To get connection object
 ```
connectionFactoryWrapper.connectionFactory().create()
```
 - prepare a sql statement
 ```
StatementWrapper statementWrapper = new StatementWrapper(connection.createStatement(query));
```
 - execute the sql statement and retrieve the rows details 
```
                    Flux.from(statementWrapper.getStatement().execute())
                            .flatMap(result -> result.map(row -> {
```
 - to release the connection to pool
```
connection -> connection.close()
```

file: CatalogRepository.java
```
@Slf4j
public class CatalogRepository {

    private Map<String, String> columnMap = new HashMap<String, String>() {
        {
            put("catalogId", "catalog_id");
            put("catalogName", "catalog_name");
            put("active", "is_active");
            put("deleted", "is_deleted");
        }
    };

    private List<String> booleanProperties = Arrays.asList("active", "deleted");

    @Inject
    private R2dbcConnectionFactroyWrapper connectionFactoryWrapper;

    public CompletionStage<GetCatalogsResponse> getCatalogs(Optional<String> filter, Optional<String> limit, Optional<String> offset) {
        return Mono.usingWhen(connectionFactoryWrapper.connectionFactory().create(),
                connection -> {
                    String filterQuery = filter
                            .map(f -> {
                                log.info("filter::{}", f);
                                return Arrays.asList(f.split(",")).stream()
                                        .map(c -> Arrays.asList(c.split("::")))
                                        .filter(m -> m.size() == 2)
                                        .map(m -> {
                                            String columnName = columnMap.get(m.get(0));
                                            boolean isBool = booleanProperties.contains(m.get(0));
                                            return columnName+ "=" +(isBool?m.get(1):("'"+m.get(1)+"'"));
                                        })
                                        .collect(Collectors.joining(" and "));
                            })
                            .filter(StringUtils::isNotBlank)
                            .map(c -> "where "+c)
                            .orElseGet(()->"");
                    String pageSize = limit.orElseGet(()->"10");
                    String offSetValue = offset.orElseGet(()->"0");
                    String query = "select catalog_id, catalog_name, is_active, is_deleted, COUNT(1) OVER () as TOTAL_COUNT from catalog " +
                            filterQuery + " OFFSET "+offSetValue+" LIMIT "+pageSize;

                    AtomicInteger searchTotalCount = new AtomicInteger();
                    log.info("query::{}", query);
                    StatementWrapper statementWrapper = new StatementWrapper(connection.createStatement(query));
                    return Flux.from(statementWrapper.getStatement().execute())
                            .flatMap(result -> result.map(row -> {
                                searchTotalCount.set(row.get("TOTAL_COUNT", Integer.class));
                                return GetCatalogResponse.builder()
                                        .catalogId(row.get("catalog_id", String.class))
                                        .catalogName(row.get("catalog_name", String.class))
                                        .active(row.get("is_active", Boolean.class))
                                        .deleted(row.get("is_deleted", Boolean.class))
                                        .build();
                            }))
                            .collectList()
                            .map(list -> Optional.ofNullable(list))
                            .switchIfEmpty(Mono.just(Optional.empty()))
                            .map(optList -> {
                                List<GetCatalogResponse> list = new ArrayList<>();
                                if(optList.isPresent()){
                                    list.addAll(optList.get());
                                }
                                GetCatalogsResponse.Pagination pagination =
                                        new GetCatalogsResponse.Pagination(
                                                searchTotalCount.get(),
                                                Integer.parseInt(pageSize),
                                                Integer.parseInt(offSetValue)
                                        );
                                return GetCatalogsResponse.builder()
                                        .pagination(pagination)
                                        .catalogs(list)
                                        .build();
                            });
                },
                connection -> connection.close()).toFuture();
    }

}
```

