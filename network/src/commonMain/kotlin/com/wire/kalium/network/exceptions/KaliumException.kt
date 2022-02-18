package com.wire.kalium.network.exceptions

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.message.SendMessageResponse

sealed class KaliumException : Exception() {
    /**
     * http error 300 .. 399
     */
    class RedirectError(val errorResponse: ErrorResponse) : KaliumException()

    /**
     * http error 400 .. 499
     */
    class InvalidRequestError(val errorResponse: ErrorResponse) : KaliumException()

    /**
     * http error 500 .. 599
     */
    class ServerError(val errorResponse: ErrorResponse) : KaliumException()

    /**
     * Generic errors e.g. Serialization errors
     */
    class GenericError(override val cause: Throwable?) : KaliumException()

    /**
     * IOException ?
     */
    class NetworkUnavailableError(override val cause: Throwable) :
        KaliumException()

    sealed class FeatureError() : KaliumException()
}

sealed class SendMessageError : KaliumException.FeatureError() {
    class MissingDeviceError(val errorBody: SendMessageResponse.MissingDevicesResponse) : SendMessageError()
}
