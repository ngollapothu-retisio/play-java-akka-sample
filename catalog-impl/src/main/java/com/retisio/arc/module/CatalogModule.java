package com.retisio.arc.module;

import com.google.inject.AbstractModule;
import com.retisio.arc.controller.CatalogServiceController;
import com.retisio.arc.execution.ServiceExecutionContext;
import com.retisio.arc.service.CatalogService;
import com.retisio.arc.service.CatalogServiceImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CatalogModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CatalogServiceController.class).asEagerSingleton();
        bind(CatalogService.class).to(CatalogServiceImpl.class).asEagerSingleton();
        bind(ServiceExecutionContext.class).asEagerSingleton();
        log.info("Depence injection is configured ....");
    }

}
