package com.retisio.arc.exception;

import lombok.extern.slf4j.Slf4j;
import play.http.HttpErrorHandler;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@Slf4j
public class ApplicationExceptionHandler implements HttpErrorHandler {
    @Override
    public CompletionStage<Result> onClientError(Http.RequestHeader request, int statusCode, String message) {
        log.warn("Client side error encountered. statusCode::{}", statusCode);
        Map<String, Object> response = new HashMap<>();
        response.put("status", statusCode);
        response.put("error", message);
        return CompletableFuture.completedFuture(
                Results.status(statusCode, Json.toJson(response))
        );
    }

    @Override
    public CompletionStage<Result> onServerError(Http.RequestHeader request, Throwable throwable) {
        log.error("Serveer side error encountered.", throwable);
        Throwable exception = unwrapException(throwable);
        if (exception instanceof AlreadyExistsException
                || exception instanceof IllegalOperationException
                || exception instanceof NotFoundException
        ) {
            CustomException customException = (CustomException)exception;
            log.info("errorCode::{}, errors::{}", customException.getCode(), customException.getErrors());
            return CompletableFuture.completedFuture(
                    Results.badRequest(Json.toJson(
                            ErrorResponse.builder()
                                    .status(customException.getCode())
                                    .message("Fix the errors")
                                    .errors(customException.getErrors())
                                    .build()
                        )
                    )
            );
        }

        return CompletableFuture.completedFuture(
                Results.internalServerError(Json.toJson(
                        ErrorResponse.builder()
                        .status("500")
                        .message("InternalServerError")
                        .errors(Arrays.asList(new Error("SERVER_ERROR", exception.getMessage())))
                        .build()
                    )
                )
        );
    }


    /**
     * Unwrap the given exception from known exception wrapper types.
     */
    private Throwable unwrapException(Throwable exception) {
        if (exception.getCause() != null) {
            if (exception instanceof ExecutionException
                    || exception instanceof InvocationTargetException
                    || exception instanceof CompletionException) {
                return unwrapException(exception.getCause());
            }
        }
        return exception;
    }
}