package com.wire.kalium.exceptions

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// TODO: return custom error instead of throwing exception

sealed class KaliumException : Exception()

@JsonIgnoreProperties(ignoreUnknown = true)
data class HttpException @JsonCreator constructor(
        @JsonProperty("message") override val message: String? = null,
        @JsonProperty("code") val code: Int = 0,
        @JsonProperty("label") val label: String? = null
) : KaliumException() {
    override fun toString(): String {
        val clazz = javaClass.simpleName
        return String.format("%s: code: %d, msg: %s, label: %s", clazz, code, message, label)
    }
}
