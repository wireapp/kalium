package com.wire.kalium.network.exceptions

import com.wire.kalium.network.api.ErrorResponse

sealed class KaliumException : Exception() {
    class RedirectError(code: Int, throwable: Throwable?): KaliumException()
    class InvalidRequestError(errorResponse: ErrorResponse, throwable: Throwable?): KaliumException()
    class ServerError(errorResponse: ErrorResponse, throwable: Throwable?): KaliumException()
    class GenericError(errorResponse: ErrorResponse, throwable: Throwable?): KaliumException()
}
