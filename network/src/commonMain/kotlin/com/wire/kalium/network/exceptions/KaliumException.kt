package com.wire.kalium.network.exceptions

import com.wire.kalium.network.api.ErrorResponse

sealed class KaliumException(val errorResponse: ErrorResponse) : Exception() {
    class RedirectError(errorResponse: ErrorResponse, throwable: Throwable?): KaliumException(errorResponse)
    class InvalidRequestError(errorResponse: ErrorResponse, throwable: Throwable?): KaliumException(errorResponse)
    class ServerError(errorResponse: ErrorResponse, throwable: Throwable?): KaliumException(errorResponse)
    class GenericError(errorResponse: ErrorResponse, throwable: Throwable?): KaliumException(errorResponse)
}
