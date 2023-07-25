package com.retisio.arc.exception;

import java.util.List;

public class AlreadyExistsException extends CustomException {

    public AlreadyExistsException(List<Error> errors) {
        super(errors);
    }

    public AlreadyExistsException(String code, List<Error> errors) {
        super(code, errors);
    }

    public AlreadyExistsException(String code, String message, List<Error> errors) {
        super(code, message, errors);
    }

    public AlreadyExistsException(String code, String message, Throwable cause, List<Error> errors) {
        super(code, message, cause, errors);
    }

    public AlreadyExistsException(String code, Throwable cause, List<Error> errors) {
        super(code, cause, errors);
    }

    protected AlreadyExistsException(String code, String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, List<Error> errors) {
        super(code, message, cause, enableSuppression, writableStackTrace, errors);
    }
}
