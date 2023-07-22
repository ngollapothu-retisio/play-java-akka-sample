package com.retisio.arc.r2dbc;

import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.persistence.r2dbc.ConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

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
