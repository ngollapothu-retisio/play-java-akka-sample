package com.retisio.arc.exception;

import java.util.List;

public class CustomException extends RuntimeException {
    private String code;
    private List<Error> errors;

    public CustomException(List<Error> errors) {
        super();
        this.code = "400";
        this.errors = errors;
    }
    public CustomException(String code, List<Error> errors) {
        super();
        this.code = code;
        this.errors = errors;
    }

    public CustomException(String code, String message, List<Error> errors) {
        super(message);
        this.errors = errors;
    }

    public CustomException(String code, String message, Throwable cause, List<Error> errors) {
        super(message, cause);
        this.errors = errors;
    }

    public CustomException(String code, Throwable cause, List<Error> errors) {
        super(cause);
        this.errors = errors;
    }

    protected CustomException(String code, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, List<Error> errors) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.errors = errors;
    }
    public String getCode(){
        return this.code;
    }
    public List<Error> getErrors(){
        return this.errors;
    }
}
