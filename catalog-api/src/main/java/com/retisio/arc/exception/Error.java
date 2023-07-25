package com.retisio.arc.exception;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

@Value
@JsonSerialize
public class Error {
    private String code;
    private String message;
}
