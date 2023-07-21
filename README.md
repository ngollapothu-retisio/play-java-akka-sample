# play-java-akka-sample

Steps to run application

Step 1: clone the code
```
git clone https://github.com/ngollapothu-retisio/play-java-akka-sample.git
git checkout 0.3-akka-db-projection
git pull
```
Step 2: execute DDL scripts on respective databases at bash command prompt

user: postgres, pwd: postgres
```
$ ./migrate-read-db.sh postgres postgres localhost:5432
```

Step 3: run the service in local
```
sbt -Dconfig.resource=application-local.conf run
```

Step 4: 

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

Step 5: 
db: catalog_read_side
```
select * from catalog;

select * from akka_projection_timestamp_offset_store;
```

About the code

Before jumping to the Akka DB Projection code changes, please check the below links

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.1-akka-management-enabled/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.2-akka-persistence/README.md

New files are added in this branch
```
1. CatalogDbProjection.java
   CatalogDbProjectionHandler.java
2. StatementWrapper.java
3. r2dbc-read-side.conf
4. sql/catalog_read_side/V2__catalog_readside_1.sql
```

**Details:**

1. Akka projection DDL scripts are available in sql/catalog_read_side/V1__Intial_r2dbc_read_1.sql. These tables are different from legacy lagom read side DDL structure.
```
$ ./migrate-read-db.sh postgres postgres localhost:5432
```
2. sql/catalog_read_side/V2__catalog_readside_1.sql has Catalog DB projection DDL, Event details will be stored in CATALOG table in read side database.
```
DROP TABLE IF EXISTS CATALOG;

CREATE TABLE IF NOT EXISTS CATALOG
(
    CATALOG_ID VARCHAR(255) NOT NULL,
    CATALOG_NAME VARCHAR(500) NOT NULL,
    IS_ACTIVE BOOLEAN DEFAULT FALSE,
    IS_DELETED BOOLEAN DEFAULT FALSE,
    CREATED_TMST TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    LAST_MODIFIED_TMST TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(CATALOG_ID)
);
```
3. In CatalogDbProjection, 4 Sharded Daemon threads are created and will be executed on multiple pods.
For example, micro-service is running on 2 pods then 2 threads will be running on each pod.
And If micro-service is running on 3 pods then 2 threads are on one pod, 1 thread on one pad,and 1 thread on another pod.
And If micro-service is running on 4 pods then 1 thread on each pod.  

go through the classes for best practices
```
   CatalogDbProjection.java
   CatalogDbProjectionHandler.java
```
file: CatalogDbProjection.java
```
    public static void init(ActorSystem system) {
        // Split the slices into 4 ranges
        int numberOfSliceRanges = 4;
        List<Pair<Integer, Integer>> sliceRanges =
                EventSourcedProvider.sliceRanges(
                        system, R2dbcReadJournal.Identifier(), numberOfSliceRanges);

        ShardedDaemonProcess.get(system)
                .init(
                        ProjectionBehavior.Command.class,
                        "CatalogDbProjection",
                        sliceRanges.size(),
                        i -> ProjectionBehavior.create(createProjection(system, sliceRanges.get(i))),
                        ProjectionBehavior.stopMessage());
    }
```
ProjectionId should be unique for each slice range, "CatalogDbProjection" is projection name.
file: CatalogDbProjection.java
```
ProjectionId projectionId =
                ProjectionId.of("CatalogDbProjection", "catalog-db-" + minSlice + "-" + maxSlice);
```

4. CatalogDbProjectionHandler receives event details in process method along with R2dbcSession object. We can use R2dbcSession to persist the event details in CATALOG table.
```
@Slf4j
public class CatalogDbProjectionHandler extends R2dbcHandler<EventEnvelope<CatalogEvent>> {

    @Override
    public CompletionStage<Done> process(R2dbcSession session, EventEnvelope<CatalogEvent> envelope) {
        CatalogEvent event = envelope.event();
        return processReadSide(session, event);
    }

    private CompletionStage<Done> processReadSide(R2dbcSession session, CatalogEvent event){
        if(event instanceof CatalogEvent.CatalogCreated) {
            return saveCatalog(session, (CatalogEvent.CatalogCreated)event);
        }else {
            return CompletableFuture.completedFuture(Done.getInstance());
        }
    }
    private static final String SAVE_CATALOG_QUERY = "INSERT INTO CATALOG(" +
		"CATALOG_ID, " +
		"CATALOG_NAME, " +
		"IS_ACTIVE, " +
		"IS_DELETED, " +
        "LAST_MODIFIED_TMST) " +
        "VALUES ($1, $2, $3, $4, $5) " +
        "on conflict (CATALOG_ID) DO UPDATE set " +
		"CATALOG_ID=excluded.CATALOG_ID, " +
		"CATALOG_NAME=excluded.CATALOG_NAME, " +
		"IS_ACTIVE=excluded.IS_ACTIVE, " +
		"IS_DELETED=excluded.IS_DELETED, " +
        "LAST_MODIFIED_TMST=now()";
    private CompletionStage<Done> saveCatalog(R2dbcSession session, CatalogEvent.CatalogCreated event) {
    	log.info("saveCatalog catalogId::{}", event.catalogId);
    	AtomicInteger index = new AtomicInteger(-1);
        StatementWrapper statementWrapper = new StatementWrapper(session.createStatement(SAVE_CATALOG_QUERY));
        statementWrapper.bind(index.incrementAndGet(), event.catalogId, String.class);
        statementWrapper.bind(index.incrementAndGet(), event.getCatalogName(), String.class);
        statementWrapper.bind(index.incrementAndGet(), event.getActive(), Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), false, Boolean.class);
        statementWrapper.bind(index.incrementAndGet(), Timestamp.valueOf(LocalDateTime.now()), Timestamp.class);
        return session.updateOne(statementWrapper.getStatement())
                .thenApply(rowsUpdated -> Done.getInstance());
    }

}
``` 
StatementWrapper.java is just a helper class to bind the params of SQL query with statement, It is simplifying the code. 
```
public class StatementWrapper {
    private Statement stmt;
    public StatementWrapper(Statement stmt){
        this.stmt = stmt;
    }
    public StatementWrapper bind(int index, Object object, Class<?> type){
        return Optional.ofNullable(object)
                .map(o -> {
                    this.stmt.bind(index, object);
                    return this;
                })
                .orElseGet(()->{
                    this.stmt.bindNull(index, type);
                    return this;
                });
    }
    public Statement getStatement(){
        return this.stmt;
    }
}
//$1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20
```

5. To enabled Akka projection include this file in application.conf
```
include "r2dbc-read-side"
```

6. register the CatalogDbProjection 

file: CatalogServiceImpl.java
```
    @Inject
    public CatalogServiceImpl(ActorSystem classicActorSystem){
        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);
        this.clusterSharding = ClusterSharding.get(typedActorSystem);

        CatalogAggregate.init(typedActorSystem, 3,35); //akka-persistence

        CatalogDbProjection.init(typedActorSystem); //akka-projection
    }

```
