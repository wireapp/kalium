package com.wire.kalium.exceptions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// TODO: return custom error instead of throwing exception

@Serializable
sealed class KaliumException : Exception()

@Serializable
sealed class NetworkException(
        open var _message: String? = null,
        open var _code: Int = 0,
        open var _label: String? = null
) : KaliumException()

@Serializable
data class HttpException(
        @SerialName("message") override var _message: String? = null,
        @SerialName("code") override var _code: Int = 0,
        @SerialName("label") override var _label: String? = null,

        ) : NetworkException(_message) {


    constructor(message: String?,
                code: Int) : this(_message = message) {
        this._code = code

        this._message = message
    }


    override fun toString(): String {
        val clazz = javaClass.simpleName
        return "$clazz: code: $_code, msg: $message, label: $_label"
    }
}

@Serializable
class AuthException : NetworkException {
    constructor(message: String?, code: Int) : super(_message = message, _code = code)
    constructor(code: Int) : super(_code = code)

    constructor( message: String?,
                code: Int,
                label: String?) : super(message, code, label)
}
