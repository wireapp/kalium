package com.wire.kalium.network.exceptions

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
class AuthException : NetworkException {
    constructor(message: String?, code: Int) : super(_message = message, _code = code)
    constructor(code: Int) : super(_code = code)

    constructor(
        message: String?,
        code: Int,
        label: String?
    ) : super(message, code, label)
}
