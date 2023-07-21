# play-java-akka-sample

Steps to run application

Step 1: clone the code
```
git clone https://github.com/ngollapothu-retisio/play-java-akka-sample.git
git checkout 0.4-akka-message-projection
git pull
```
Step 2: run the service in local
```
sbt -Dconfig.resource=application-local.conf run
```

Step 3: 

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

Step 4: check catalog messsage projection status in read side database 
db: catalog_read_side
```
select * from akka_projection_timestamp_offset_store;
```

step 5: validate message in kafka topic 'catalog-events'
```
C:\tools\kafka_2.11-2.4.1>bin\windows\kafka-console-consumer.bat --bootstrap-server localhost:9092 --topic catalog-events
{"event":"catalog-created","catalogId":"cat-001","catalog":{"catalogId":"cat-001","catalogName":"cat-001","active":true,"deleted":false}}
```


About the code

Please check the below links

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.1-akka-management-enabled/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.2-akka-persistence/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.3-akka-db-projection/README.md

New files are added in this branch
```
1. CatalogMessageProjection.java
   CatalogMessageProjectionHandler.java
   CatalogMessage
2. KafkaUtil.java
3. kafka.conf

```

**Details:**

1. In CatalogMessageProjection, Event details will be sent in message format to kafka topic. 4 Sharded Daemon threads are created and will be executed on multiple pods.
For example, micro-service is running on 2 pods then 2 threads will be running on each pod.
And If micro-service is running on 3 pods then 2 threads are on one pod, 1 thread on one pad,and 1 thread on another pod.
And If micro-service is running on 4 pods then 1 thread on each pod.  

go through the classes for best practices
```
   CatalogMessageProjection.java
   CatalogMessageProjectionHandler.java
```
file: CatalogMessageProjection.java
```
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
                            "CatalogMessageProjection",
                            sliceRanges.size(),
                            i -> ProjectionBehavior.create(createProjection(system, sliceRanges.get(i), topic, kafkaUtil)),
                            ProjectionBehavior.stopMessage());
        }
```
ProjectionId should be unique for each slice range, "CatalogMessageProjection" is projection name.
file: CatalogMessageProjection.java
```
ProjectionId projectionId =
                ProjectionId.of("CatalogMessageProjection", "catalog-message-" + minSlice + "-" + maxSlice);
```

2. CatalogMessageProjectionHandler receives event details in process method along with R2dbcSession object.
```
@Slf4j
public class CatalogMessageProjectionHandler extends R2dbcHandler<EventEnvelope<CatalogEvent>> {

    private final Duration askTimeout = Duration.ofSeconds(5);
    private final String topic;
    private final KafkaUtil kafkaUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClusterSharding clusterSharding;

    public CatalogMessageProjectionHandler(ClusterSharding clusterSharding, String topic, KafkaUtil kafkaUtil) {
        this.topic = topic;
        this.kafkaUtil = kafkaUtil;
        this.clusterSharding = clusterSharding;
        objectMapper.registerModule(new DefaultScalaModule());
    }

    @Override
    public CompletionStage<Done> process(R2dbcSession session, EventEnvelope<CatalogEvent> envelope) {
        CatalogEvent event = envelope.event();
        return sendToKafkaTopic(event);
    }
    public EntityRef<CatalogCommand> ref(String id) {
        return clusterSharding.entityRefFor(CatalogAggregate.ENTITY_TYPE_KEY, id);
    }
    public CompletionStage<Optional<Catalog>> getCatalog(EntityRef<CatalogCommand> ref) {
        return ref.<Optional<Catalog>>ask(replyTo -> new CatalogCommand.GetCatalog(replyTo), askTimeout);
    }
    private String toJsonString(Object object){
        if(object == null){
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }
    private CompletionStage<Done> sendToKafkaTopic(CatalogEvent event){
        if (event instanceof CatalogEvent.CatalogCreated) {
            return getCatalog(ref(event.catalogId))
                    .thenApply(optionalCatalog -> {
                        if(optionalCatalog.isPresent()){
                            kafkaUtil.send(topic, event.catalogId, toJsonString(convertToCatalogPublishEvent(event, optionalCatalog.get())));
                            log.info("Catalog message is published to topic::{}, key::{}", topic, event.catalogId);
                        } else {
                            log.warn("Catalog data for id::{} is not found to publish message to topic::{}", event.catalogId, topic);
                        }
                        return Done.getInstance();
                    });
        } else {
            log.debug("event {} is not eligible to send.", event.getClass().getName());
            return CompletableFuture.completedFuture(Done.getInstance());
        }
    }

    public static CatalogMessage convertToCatalogPublishEvent(CatalogEvent event, Catalog catalog) {
        if(event instanceof CatalogEvent.CatalogCreated){
            return new CatalogMessage.CatalogCreatedMessage(convertToCatalogCreatedMessage(catalog));
        } else {
            log.error("Try to convert non publish CatalogEvent: {}", event);
            throw new IllegalArgumentException("non publish CatalogEvent");
        }
    }

    private static CatalogMessage.Catalog convertToCatalogCreatedMessage(Catalog catalog) {
        return new CatalogMessage.Catalog(
                catalog.getCatalogId(),
                catalog.getCatalogId(),
                catalog.getActive(),
                catalog.getDeleted()
        );
    }
}
``` 
KafkaUtil.java is used to send Kafka message to topic 
```
@Singleton
@Slf4j
public class KafkaUtil {

    private final ProducerSettings<String, String> kafkaProducerSettings;
    private final org.apache.kafka.clients.producer.Producer<String, String> kafkaProducer;

    @Inject
    public KafkaUtil(ActorSystem system, Config config) {
        kafkaProducerSettings = ProducerSettings.create(system, new StringSerializer(), new StringSerializer())
                .withBootstrapServers(config.getString("kafka-connection-settings.bootstrap.servers"));
        kafkaProducer = kafkaProducerSettings.createKafkaProducer();
    }

    public void send(String topic, String key, String value) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<String, String>(topic, key, value);
            kafkaProducer.send(record, (recordMetadata, exception) -> {
                if (exception == null) {
                    log.info("Topic::{}, Partition::{}, Offset:{},keySize::{}, valueSize::{}",
                            recordMetadata.topic(),
                            recordMetadata.partition(),
                            recordMetadata.offset(),
                            recordMetadata.serializedKeySize(),
                            recordMetadata.serializedValueSize());
                } else {
                    log.error("KafkaError::000 while sending message to Topic::{}, key::{}, value::{}",
                            topic, key, value, exception);
                }
            });
        }catch (Exception e){
            log.error("KafkaError:001 while sending message to Topic::{}, key::{}, value::{}",
                    topic, key, value, e);
        }

    }
}
```

5. include kafka.conf file in application.conf
```
include "kafka"
```
file: kafka.conf
```
catalog.kafka.message.topic = "catalog-events"

# common config for akka.kafka.producer.kafka-clients and akka.kafka.consumer.kafka-clients
kafka-connection-settings {
  # This and other connection settings may have to be changed depending on environment.
  bootstrap.servers = "localhost:9092"
  bootstrap.servers = ${?KAFKA_BOOTSTRAP_SERVERS}
}
akka.kafka.producer {
  kafka-clients = ${kafka-connection-settings}
}
akka.kafka.consumer {
  kafka-clients = ${kafka-connection-settings}
}
```

6. register the CatalogMessageProjection 

file: CatalogServiceImpl.java
```
    @Inject
    public CatalogServiceImpl(ActorSystem classicActorSystem, KafkaUtil kafkaUtil){
        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);
        this.clusterSharding = ClusterSharding.get(typedActorSystem);

        CatalogAggregate.init(typedActorSystem, 3,35);

        CatalogDbProjection.init(typedActorSystem);
        CatalogMessageProjection.init(typedActorSystem, kafkaUtil);
    }

```
