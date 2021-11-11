package com.wire.kalium.exceptions

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// TODO: return custom error instead of throwing exception

sealed class KaliumException: Exception()

sealed class NetworkException(
        override var message: String? = null,
        var code: Int = 0,
        var label: String? = null
): KaliumException()

@JsonIgnoreProperties(ignoreUnknown = true)
class HttpException : NetworkException {
    constructor(message: String?,
                code: Int) : super(message) {
        this.code = code
        this.message = message
    }

    @JsonCreator
    constructor(@JsonProperty("message") message: String?,
                @JsonProperty("code") code: Int,
                @JsonProperty("label") label: String?) : super(message) {
        this.code = code
        this.message = message
        this.label = label
    }

    constructor(code: Int) : super(code = code) {
        this.code = code
    }

    constructor() {}

    override fun toString(): String {
        val clazz = javaClass.simpleName
        return String.format("%s: code: %d, msg: %s, label: %s", clazz, code, message, label)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class AuthException : NetworkException {
    constructor(message: String?, code: Int) : super(message = message, code = code)
    constructor(code: Int) : super(code = code)

    @JsonCreator
    constructor(@JsonProperty("message") message: String?,
                @JsonProperty("code") code: Int,
                @JsonProperty("label") label: String?) : super(message, code, label)
}
