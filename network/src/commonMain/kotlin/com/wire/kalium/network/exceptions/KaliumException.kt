package com.wire.kalium.network.exceptions

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.message.SendMessageResponse
import com.wire.kalium.network.exceptions.NetworkErrorLabel.BAD_REQUEST
import com.wire.kalium.network.exceptions.NetworkErrorLabel.BLACKLISTED_EMAIL
import com.wire.kalium.network.exceptions.NetworkErrorLabel.DOMAIN_BLOCKED
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_CREDENTIALS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_EMAIL
import com.wire.kalium.network.exceptions.NetworkErrorLabel.KEY_EXISTS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.MISSING_AUTH
import com.wire.kalium.network.exceptions.NetworkErrorLabel.TOO_MANY_CLIENTS

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

    sealed class FeatureError() : KaliumException()
}

sealed class SendMessageError : KaliumException.FeatureError() {
    class MissingDeviceError(val errorBody: SendMessageResponse.MissingDevicesResponse) : SendMessageError()
}

sealed class QualifiedSendMessageError() : KaliumException.FeatureError() {
    class MissingDeviceError(
        val errorBody: QualifiedSendMessageResponse.MissingDevicesResponse
    ) : QualifiedSendMessageError()
}


fun KaliumException.InvalidRequestError.isInvalidCredentials(): Boolean {
    return errorResponse.label == INVALID_CREDENTIALS
}

fun KaliumException.InvalidRequestError.isTooManyClients(): Boolean {
    return errorResponse.label == TOO_MANY_CLIENTS
}

fun KaliumException.InvalidRequestError.isMissingAuth(): Boolean {
    return errorResponse.label == MISSING_AUTH
}

fun KaliumException.InvalidRequestError.isBadRequest(): Boolean {
    return errorResponse.label == BAD_REQUEST
}

fun KaliumException.InvalidRequestError.isDomainBlocked(): Boolean {
    return errorResponse.label == DOMAIN_BLOCKED
}

fun KaliumException.InvalidRequestError.isKeyExists(): Boolean {
    return errorResponse.label == KEY_EXISTS
}

fun KaliumException.InvalidRequestError.isBlackListedEmail(): Boolean {
    return errorResponse.label == BLACKLISTED_EMAIL
}

fun KaliumException.InvalidRequestError.isInvalidEmail(): Boolean {
    return errorResponse.label == INVALID_EMAIL
}
