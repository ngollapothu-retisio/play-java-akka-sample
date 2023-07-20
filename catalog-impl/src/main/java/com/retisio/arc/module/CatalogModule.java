package com.retisio.arc.module;

import com.google.inject.AbstractModule;
import com.retisio.arc.execution.ServiceExecutionContext;
import com.retisio.arc.service.CatalogService;
import com.retisio.arc.service.CatalogServiceImpl;

public class CatalogModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CatalogService.class).to(CatalogServiceImpl.class).asEagerSingleton();
        bind(ServiceExecutionContext.class).asEagerSingleton();
    }

}
