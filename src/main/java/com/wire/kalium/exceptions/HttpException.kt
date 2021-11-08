package com.wire.kalium.exceptions

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
open class HttpException : Exception {
    private var code = 0
    private var message: String? = null
    private var label: String? = null

    constructor(
        message: String?,
        code: Int
    ) : super(message) {
        this.code = code
        this.message = message
    }

    @JsonCreator
    constructor(
        @JsonProperty("message") message: String?,
        @JsonProperty("code") code: Int,
        @JsonProperty("label") label: String?
    ) : super(message) {
        this.code = code
        this.message = message
        this.label = label
    }

    constructor(code: Int) {
        this.code = code
    }

    constructor() {}

    override fun toString(): String {
        val clazz = javaClass.simpleName
        return String.format("%s: code: %d, msg: %s, label: %s", clazz, code, message, label)
    }

    fun getCode(): Int {
        return code
    }

    fun setCode(code: Int) {
        this.code = code
    }

    override fun getMessage(): String? {
        return message
    }

    fun setMessage(message: String?) {
        this.message = message
    }

    fun getLabel(): String? {
        return label
    }

    fun setLabel(label: String?) {
        this.label = label
    }
}
