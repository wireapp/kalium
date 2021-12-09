package com.wire.kalium.network.exceptions

sealed class KaliumException : Exception() {
    class RedirectError(code: Int, throwable: Throwable?): KaliumException()
    class InvalidRequestError(code: Int, throwable: Throwable?): KaliumException()
    class ServerError(code: Int, throwable: Throwable?): KaliumException()
    class GenericError(code: Int, throwable: Throwable?): KaliumException()
}
