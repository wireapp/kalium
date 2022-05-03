package com.wire.kalium.network.exceptions

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.message.SendMessageResponse
import com.wire.kalium.network.exceptions.NetworkErrorLabel.BAD_REQUEST
import com.wire.kalium.network.exceptions.NetworkErrorLabel.BLACKLISTED_EMAIL
import com.wire.kalium.network.exceptions.NetworkErrorLabel.DOMAIN_BLOCKED_FOR_REGISTRATION
import com.wire.kalium.network.exceptions.NetworkErrorLabel.HANDLE_EXISTS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_CODE
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_CREDENTIALS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_EMAIL
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_HANDLE
import com.wire.kalium.network.exceptions.NetworkErrorLabel.KEY_EXISTS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.MISSING_AUTH
import com.wire.kalium.network.exceptions.NetworkErrorLabel.TOO_MANY_CLIENTS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.TOO_MANY_MEMBERS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.USER_CREATION_RESTRICTED

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

data class ProteusClientsChangedError(
    val errorBody: QualifiedSendMessageResponse.MissingDevicesResponse
) : KaliumException.FeatureError()

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

fun KaliumException.InvalidRequestError.isDomainBlockedForRegistration(): Boolean {
    return errorResponse.label == DOMAIN_BLOCKED_FOR_REGISTRATION
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

fun KaliumException.InvalidRequestError.isInvalidHandle(): Boolean {
    return errorResponse.label == INVALID_HANDLE
}

fun KaliumException.InvalidRequestError.isHandleExists(): Boolean {
    return errorResponse.label == HANDLE_EXISTS
}

fun KaliumException.InvalidRequestError.isInvalidCode(): Boolean {
    return errorResponse.label == INVALID_CODE
}

fun KaliumException.InvalidRequestError.isUserCreationRestricted(): Boolean {
    return errorResponse.label == USER_CREATION_RESTRICTED
}

fun KaliumException.InvalidRequestError.isTooMAnyMembers(): Boolean {
    return errorResponse.label == TOO_MANY_MEMBERS
}
