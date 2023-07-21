package com.retisio.arc.util;

import akka.actor.ActorSystem;
import akka.kafka.ProducerSettings;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import javax.inject.Inject;
import javax.inject.Singleton;

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
