package com.wire.xenon.backend.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorMessage {
    @JsonProperty
    public String message;

    public ErrorMessage(String message) {
        this.message = message;
    }
}