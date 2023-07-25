package com.retisio.arc.exception;

import java.util.List;

public class NotFoundException extends CustomException {

    public NotFoundException(List<Error> errors) {
        super(errors);
    }

    public NotFoundException(String code, List<Error> errors) {
        super(code, errors);
    }

    public NotFoundException(String code, String message, List<Error> errors) {
        super(code, message, errors);
    }

    public NotFoundException(String code, String message, Throwable cause, List<Error> errors) {
        super(code, message, cause, errors);
    }

    public NotFoundException(String code, Throwable cause, List<Error> errors) {
        super(code, cause, errors);
    }

    protected NotFoundException(String code, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, List<Error> errors) {
        super(code, message, cause, enableSuppression, writableStackTrace, errors);
    }
}
