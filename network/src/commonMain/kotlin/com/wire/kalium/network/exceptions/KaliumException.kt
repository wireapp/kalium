/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

@file:Suppress("TooManyFunctions")

package com.wire.kalium.network.exceptions

import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.api.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.authenticated.message.SendMessageResponse
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.model.FederationConflictResponse
import com.wire.kalium.network.api.model.FederationUnreachableResponse
import com.wire.kalium.network.exceptions.NetworkErrorLabel.ACCESS_DENIED
import com.wire.kalium.network.exceptions.NetworkErrorLabel.BAD_CONNECTION_UPDATE
import com.wire.kalium.network.exceptions.NetworkErrorLabel.BAD_REQUEST
import com.wire.kalium.network.exceptions.NetworkErrorLabel.BLACKLISTED_EMAIL
import com.wire.kalium.network.exceptions.NetworkErrorLabel.DOMAIN_BLOCKED_FOR_REGISTRATION
import com.wire.kalium.network.exceptions.NetworkErrorLabel.FEDERATION_DENIED
import com.wire.kalium.network.exceptions.NetworkErrorLabel.FEDERATION_FAILURE
import com.wire.kalium.network.exceptions.NetworkErrorLabel.FEDERATION_NOT_ENABLED
import com.wire.kalium.network.exceptions.NetworkErrorLabel.GUEST_LINKS_DISABLED
import com.wire.kalium.network.exceptions.NetworkErrorLabel.HANDLE_EXISTS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_CODE
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_CREDENTIALS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_EMAIL
import com.wire.kalium.network.exceptions.NetworkErrorLabel.INVALID_HANDLE
import com.wire.kalium.network.exceptions.NetworkErrorLabel.KEY_EXISTS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.MISSING_AUTH
import com.wire.kalium.network.exceptions.NetworkErrorLabel.MISSING_LEGALHOLD_CONSENT
import com.wire.kalium.network.exceptions.NetworkErrorLabel.MLS_CLIENT_MISMATCH
import com.wire.kalium.network.exceptions.NetworkErrorLabel.MLS_COMMIT_MISSING_REFERENCES
import com.wire.kalium.network.exceptions.NetworkErrorLabel.MLS_MISSING_GROUP_INFO
import com.wire.kalium.network.exceptions.NetworkErrorLabel.MLS_PROTOCOL_ERROR
import com.wire.kalium.network.exceptions.NetworkErrorLabel.MLS_STALE_MESSAGE
import com.wire.kalium.network.exceptions.NetworkErrorLabel.NOT_FOUND
import com.wire.kalium.network.exceptions.NetworkErrorLabel.NOT_TEAM_MEMBER
import com.wire.kalium.network.exceptions.NetworkErrorLabel.NO_CONVERSATION
import com.wire.kalium.network.exceptions.NetworkErrorLabel.NO_CONVERSATION_CODE
import com.wire.kalium.network.exceptions.NetworkErrorLabel.NO_TEAM
import com.wire.kalium.network.exceptions.NetworkErrorLabel.OPERATION_DENIED
import com.wire.kalium.network.exceptions.NetworkErrorLabel.TOO_MANY_CLIENTS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.TOO_MANY_MEMBERS
import com.wire.kalium.network.exceptions.NetworkErrorLabel.UNKNOWN_CLIENT
import com.wire.kalium.network.exceptions.NetworkErrorLabel.USER_CREATION_RESTRICTED
import com.wire.kalium.network.exceptions.NetworkErrorLabel.WRONG_CONVERSATION_PASSWORD
import io.ktor.http.HttpStatusCode

sealed class KaliumException : Exception() {

    data class NoNetwork(val networkState: NetworkState = NetworkState.NotConnected) : KaliumException()

    data class Unauthorized(val errorCode: Int) : KaliumException()

    /**
     * http error 300 .. 399
     */
    data class RedirectError(val errorResponse: ErrorResponse) : KaliumException()

    /**
     * http error 400 .. 499
     */
    data class InvalidRequestError(val errorResponse: ErrorResponse) : KaliumException()

    /**
     * http error 500 .. 599
     */
    data class ServerError(val errorResponse: ErrorResponse) : KaliumException()

    /**
     * Generic errors e.g. Serialization errors
     */
    data class GenericError(override val cause: Throwable) : KaliumException()

    /**
     * Federation errors types
     */
    data class FederationError(val errorResponse: ErrorResponse) : KaliumException()

    data class FederationConflictException(val errorResponse: FederationConflictResponse) : KaliumException()
    data class FederationUnreachableException(val errorResponse: FederationUnreachableResponse) : KaliumException()

    sealed class FeatureError : KaliumException()
}

sealed class SendMessageError : KaliumException.FeatureError() {
    class MissingDeviceError(val errorBody: SendMessageResponse.MissingDevicesResponse) : SendMessageError()
}

data class ProteusClientsChangedError(
    val errorBody: QualifiedSendMessageResponse.MissingDevicesResponse
) : KaliumException.FeatureError()

data class APINotSupported(
    val errorBody: String
) : KaliumException.FeatureError()

fun KaliumException.InvalidRequestError.isInvalidCredentials(): Boolean {
    return errorResponse.label == INVALID_CREDENTIALS
}

fun KaliumException.InvalidRequestError.isUnknownClient(): Boolean {
    return errorResponse.label == UNKNOWN_CLIENT
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

fun KaliumException.InvalidRequestError.isNotFound(): Boolean {
    return errorResponse.code == HttpStatusCode.NotFound.value
}

fun KaliumException.InvalidRequestError.isNotFoundLabel(): Boolean {
    return errorResponse.label == NOT_FOUND
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

fun KaliumException.InvalidRequestError.isTooManyRequests(): Boolean {
    return errorResponse.code == HttpStatusCode.TooManyRequests.value
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

fun KaliumException.InvalidRequestError.isTooManyMembers(): Boolean {
    return errorResponse.label == TOO_MANY_MEMBERS
}

fun KaliumException.InvalidRequestError.isNoTeam(): Boolean {
    return errorResponse.label == NO_TEAM
}

fun KaliumException.InvalidRequestError.isOperationDenied(): Boolean {
    return errorResponse.label == OPERATION_DENIED
}

fun KaliumException.InvalidRequestError.isMlsStaleMessage(): Boolean {
    return errorResponse.label == MLS_STALE_MESSAGE
}

fun KaliumException.InvalidRequestError.isMlsClientMismatch(): Boolean {
    return errorResponse.label == MLS_CLIENT_MISMATCH
}

fun KaliumException.InvalidRequestError.isMlsCommitMissingReferences(): Boolean {
    return errorResponse.label == MLS_COMMIT_MISSING_REFERENCES
}

fun KaliumException.InvalidRequestError.isMlsMissingGroupInfo(): Boolean {
    return errorResponse.label == MLS_MISSING_GROUP_INFO
}

fun KaliumException.ServerError.isFederationError(): Boolean {
    return errorResponse.label == FEDERATION_FAILURE
}

fun KaliumException.InvalidRequestError.isNotTeamMember(): Boolean {
    return errorResponse.label == NOT_TEAM_MEMBER
}

fun KaliumException.InvalidRequestError.isConversationNotFound(): Boolean {
    return errorResponse.label == NO_CONVERSATION
}

fun KaliumException.InvalidRequestError.isConversationHasNoCode(): Boolean {
    return errorResponse.label == NO_CONVERSATION_CODE
}

fun KaliumException.InvalidRequestError.isGuestLinkDisabled(): Boolean {
    return errorResponse.label == GUEST_LINKS_DISABLED
}

fun KaliumException.InvalidRequestError.isAccessDenied(): Boolean {
    return errorResponse.label == ACCESS_DENIED
}

fun KaliumException.InvalidRequestError.isMLSProtocol(): Boolean {
    return errorResponse.label == MLS_PROTOCOL_ERROR
}

fun KaliumException.InvalidRequestError.isWrongConversationPassword(): Boolean {
    return (errorResponse.label == WRONG_CONVERSATION_PASSWORD) ||
            (errorResponse.label == BAD_REQUEST && errorResponse.message.contains("password"))
}

fun KaliumException.InvalidRequestError.isBadConnectionStatusUpdate(): Boolean {
    return errorResponse.label == BAD_CONNECTION_UPDATE
}

val KaliumException.InvalidRequestError.authenticationCodeFailure: AuthenticationCodeFailure?
    get() = AuthenticationCodeFailure.entries.firstOrNull {
        errorResponse.label == it.responseLabel
    }

fun KaliumException.FederationError.isFederationDenied() = errorResponse.label == FEDERATION_DENIED
fun KaliumException.FederationError.isFederationNotEnabled() = errorResponse.label == FEDERATION_NOT_ENABLED
fun KaliumException.InvalidRequestError.isMissingLegalHoldConsent(): Boolean = errorResponse.label == MISSING_LEGALHOLD_CONSENT
