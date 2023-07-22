# play-java-akka-sample

Steps to run application

Step 1: clone the code
```
git clone https://github.com/ngollapothu-retisio/play-java-akka-sample.git
git checkout 0.6-akka-message-listener
git pull
```
Step 2: run the service in local
```
$ ./migrate-read-db.sh postgres postgres localhost:5432

sbt -Dconfig.resource=application-local.conf run
```

Step 3: send brand message to kafka topic brand-events

```
C:\tools\kafka_2.11-2.4.1>bin\windows\kafka-console-producer.bat --broker-list localhost:9092 --topic brand-events
>{"event":"brand-created","brandId":"brand-001","brand":{"brandId":"brand-001","brandName":"LG","active":true,"deleted":false}}
```

step 4: check the brand table in read side database
```
select * from brand;
```

About the code

Please check the below links

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.1-akka-management-enabled/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.2-akka-persistence/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.3-akka-db-projection/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.4-akka-message-projection/README.md

https://github.com/ngollapothu-retisio/play-java-akka-sample/blob/0.5-akka-r2dbc-repository/README.md

New files are added in this branch
```
1. MessageHandler.java
   MessageListener.java
   BrandMessageHandler.java
2. BrandRepository.java
   BrandMessage
3. V3__brand_readside_1.sql

```

**Details:**

1. In this example we will explore Kafka message listener. MessageHandler.java, MessageListener.java are created to configure kafka lister. These are re-usable components.
BrandMessageHandler class implements MessageHandler interface,  BrandMessageHandler injected in CatalogServiceImpl constructor and invoked MessageListener.init method.
```
MessageListener.init(4, typedActorSystem,"brand-events","catalog-brand-group", brandMessageHandler);
```

file: CatalogServiceImpl.java
```
    @Inject
    public CatalogServiceImpl(ActorSystem classicActorSystem,
                              BrandMessageHandler brandMessageHandler,
                              KafkaUtil kafkaUtil){
        akka.actor.typed.ActorSystem<Void> typedActorSystem = Adapter.toTyped(classicActorSystem);
        this.clusterSharding = ClusterSharding.get(typedActorSystem);

        CatalogAggregate.init(typedActorSystem, 3,35);

        CatalogDbProjection.init(typedActorSystem);
        CatalogMessageProjection.init(typedActorSystem, kafkaUtil);

        MessageListener.init(4, typedActorSystem,"brand-events","catalog-brand-group", brandMessageHandler);
    }
```

file: MessageHandler.java
```
public interface MessageHandler {
    public CompletionStage<Done> process(ConsumerRecord<String, String> record) throws Exception;
}
```
file: MessageListener.java
```
@Slf4j
public class MessageListener {

    public static void init(int numberOfListeners, ActorSystem<?> system, String topic, String groupId, MessageHandler messageHandler) {
        for (int i = 0; i < numberOfListeners; i++) {
            init(system, topic, groupId, messageHandler);
        }
    }
    private static void init(ActorSystem<?> system, String topic, String groupId, MessageHandler messageHandler) {
        ConsumerSettings<String, String> consumerSettings =
                ConsumerSettings.create(system, new StringDeserializer(), new StringDeserializer())
                        .withGroupId(groupId);
        CommitterSettings committerSettings = CommitterSettings.create(system);

        Duration minBackoff = Duration.ofSeconds(1);
        Duration maxBackoff = Duration.ofSeconds(30);
        double randomFactor = 0.1;

        RestartSource
                .onFailuresWithBackoff(
                        RestartSettings.create(minBackoff, maxBackoff, randomFactor),
                        () -> {
                            return Consumer.committableSource(
                                    consumerSettings, Subscriptions.topics(topic))
                                    .mapAsync(
                                            1,
                                            msg -> handleRecord(messageHandler, msg.record()).thenApply(done -> msg.committableOffset()))
                                    .via(Committer.flow(committerSettings));
                        })
                .run(system);
        log.info("Listener is started for topic::{}, groupId::{}, messageHandler::{}", topic, groupId, messageHandler.getClass().getName());
    }

    private static CompletionStage<Done> handleRecord(MessageHandler messageHandler, ConsumerRecord<String, String> record)
            throws Exception {
        return messageHandler.process(record);
    }
}
```
file: BrandMessageHandler.java
```
@Slf4j
@Singleton
public class BrandMessageHandler implements MessageHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    private BrandRepository brandRepository;

    public BrandMessageHandler(){
        objectMapper.registerModule(new DefaultScalaModule());
    }

    @Override
    public CompletionStage<Done> process(ConsumerRecord<String, String> record) throws Exception {
        String message = record.value();
        log.info("message:: {}", message);
        return process(message);
    }

    public CompletionStage<Done> process(String message) {
        try {
            BrandMessage brandMessage = objectMapper.readValue(message, BrandMessage.class);
            if (Objects.nonNull(brandMessage) && brandMessage.getBrand() !=null && brandMessage.getBrand().getBrandId() != null){
                return brandRepository.saveBrand(brandMessage.getBrand());
            } else {
                log.warn("brandMessage are empty/null, hence skipping the message::{}", message);
            }
        } catch (Exception ex) {
            log.error("ERROR_IN_CONSUMING_BRAND_EVENT : {} WITH_ERROR :", message, ex);
            if (!(ex instanceof JsonMappingException || ex instanceof JsonProcessingException))
                throw new RuntimeException(ex);
        }

        return CompletableFuture.completedFuture((Done.getInstance()));
    }
}

```

