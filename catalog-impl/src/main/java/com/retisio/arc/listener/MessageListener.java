package com.retisio.arc.listener;

import akka.Done;
import akka.actor.typed.ActorSystem;
import akka.kafka.CommitterSettings;
import akka.kafka.ConsumerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Committer;
import akka.kafka.javadsl.Consumer;
import akka.stream.RestartSettings;
import akka.stream.javadsl.RestartSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

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
