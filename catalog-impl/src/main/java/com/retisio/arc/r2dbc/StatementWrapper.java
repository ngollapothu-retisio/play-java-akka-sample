package com.retisio.arc.r2dbc;

import io.r2dbc.spi.Statement;

import java.util.Optional;

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