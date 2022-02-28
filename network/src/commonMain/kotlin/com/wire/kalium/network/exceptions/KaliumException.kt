package com.wire.kalium.network.exceptions

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.message.SendMessageResponse
import kotlin.contracts.contract

sealed class KaliumException() : Exception() {

    class Unauthorized(val errorCode: Int) : KaliumException()

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
    class GenericError(override val cause: Throwable) : KaliumException()

    /**
     * IOException ?
     */
    class NetworkUnavailableError(override val cause: Throwable) :
        KaliumException()

    sealed class FeatureError() : KaliumException()

    internal companion object {
        const val ERROR_LABEL_TOO_MANY_CLIENTS = "too-many-clients"
        const val ERROR_LABEL_INVALID_CREDENTIALS = "invalid-credentials"
        const val ERROR_LABEL_BAD_REQUEST = "bad-request"
        const val ERROR_LABEL_MISSING_AUTH = "missing-auth"
    }
}

sealed class SendMessageError : KaliumException.FeatureError() {
    class MissingDeviceError(val errorBody: SendMessageResponse.MissingDevicesResponse) : SendMessageError()
}

sealed class QualifiedSendMessageError() : KaliumException.FeatureError() {
    class MissingDeviceError(
        val errorBody: QualifiedSendMessageResponse.MissingDevicesResponse
    ) : QualifiedSendMessageError()
}

fun KaliumException.InvalidRequestError.isTooManyClients(): Boolean {
    with(this.errorResponse) {
        return code == 403 && label == KaliumException.ERROR_LABEL_TOO_MANY_CLIENTS
    }
}

fun KaliumException.InvalidRequestError.isMissingAuth(): Boolean {
    with(this.errorResponse) {
        return code == 403 && label == KaliumException.ERROR_LABEL_MISSING_AUTH
    }
}

fun KaliumException.InvalidRequestError.isBadRequest(): Boolean {
    with(this.errorResponse) {
        return code == 400 && label == KaliumException.ERROR_LABEL_BAD_REQUEST
    }
}
