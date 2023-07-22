package com.retisio.arc.listener;

import akka.Done;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.concurrent.CompletionStage;

public interface MessageHandler {
    public CompletionStage<Done> process(ConsumerRecord<String, String> record) throws Exception;
}
