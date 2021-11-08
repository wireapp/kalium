package com.wire.kalium.exceptions

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
class AuthException : HttpException {
    constructor(message: String?, code: Int) : super(message, code) {}
    constructor(code: Int) : super(code) {}

    @JsonCreator
    constructor(
        @JsonProperty("message") message: String?,
        @JsonProperty("code") code: Int,
        @JsonProperty("label") label: String?
    ) : super(message, code, label) {
    }
}
