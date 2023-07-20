package com.retisio.arc.execution;

import akka.actor.ActorSystem;
import play.libs.concurrent.CustomExecutionContext;

import javax.inject.Inject;

public class ServiceExecutionContext extends CustomExecutionContext {

    @Inject
    public ServiceExecutionContext(ActorSystem actorSystem) {
        super(actorSystem, "service.execution.dispatcher");
    }

}
