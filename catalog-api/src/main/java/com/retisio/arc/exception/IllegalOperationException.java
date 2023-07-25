package com.retisio.arc.exception;

import java.util.List;

public class IllegalOperationException extends CustomException {

    public IllegalOperationException(List<Error> errors) {
        super(errors);
    }

    public IllegalOperationException(String code, List<Error> errors) {
        super(code, errors);
    }

    public IllegalOperationException(String code, String message, List<Error> errors) {
        super(code, message, errors);
    }

    public IllegalOperationException(String code, String message, Throwable cause, List<Error> errors) {
        super(code, message, cause, errors);
    }

    public IllegalOperationException(String code, Throwable cause, List<Error> errors) {
        super(code, cause, errors);
    }

    protected IllegalOperationException(String code, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, List<Error> errors) {
        super(code, message, cause, enableSuppression, writableStackTrace, errors);
    }
}
