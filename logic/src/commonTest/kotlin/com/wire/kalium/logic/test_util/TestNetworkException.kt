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

package com.wire.kalium.logic.test_util

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.AuthenticationCodeFailure
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode

object TestNetworkException {

    val generic = KaliumException.InvalidRequestError(
        ErrorResponse(400, message = "generic test error", label = "generic-test-error")
    )

    val tooManyClient = KaliumException.InvalidRequestError(
        ErrorResponse(403, message = "too many clients", label = "too-many-clients")
    )

    val missingAuth = KaliumException.InvalidRequestError(
        ErrorResponse(403, message = "missing auth", label = "missing-auth")
    )

    val badRequest = KaliumException.InvalidRequestError(
        ErrorResponse(400, message = "bad request", label = "bad-request")
    )

    val invalidCredentials = KaliumException.InvalidRequestError(
        ErrorResponse(403, message = "invalid credentials", label = "invalid-credentials")
    )

    val accessDenied = KaliumException.InvalidRequestError(
        ErrorResponse(403, message = "Access denied.", label = "access-denied")
    )

    val missingAuthenticationCode = KaliumException.InvalidRequestError(
        ErrorResponse(
            code = 403,
            message = "missing authentication code",
            label = AuthenticationCodeFailure.MISSING_AUTHENTICATION_CODE.responseLabel
        )
    )

    val invalidAuthenticationCode = KaliumException.InvalidRequestError(
        ErrorResponse(
            code = 403,
            message = "invalid authentication code",
            label = AuthenticationCodeFailure.INVALID_OR_EXPIRED_AUTHENTICATION_CODE.responseLabel
        )
    )

    val invalidHandle = KaliumException.InvalidRequestError(
        ErrorResponse(400, message = "invalid handle", label = "invalid-handle")
    )

    val invalidCode = KaliumException.InvalidRequestError(
        ErrorResponse(404, message = "invalid code", label = "invalid-code")
    )

    val notFound = KaliumException.InvalidRequestError(
        ErrorResponse(404, message = "Not Found", label = "")
    )

    val handleExists = KaliumException.InvalidRequestError(
        ErrorResponse(409, message = "handle exists", label = "handle-exists")
    )

    val invalidEmail = KaliumException.InvalidRequestError(
        ErrorResponse(400, "invalid email", "invalid-email")
    )

    val keyExists = KaliumException.InvalidRequestError(
        ErrorResponse(409, "The given e-mail address or phone number is in use.", "key-exists")
    )

    val blackListedEmail = KaliumException.InvalidRequestError(
        ErrorResponse(403, "blacklisted email", "blacklisted-email")
    )

    val userCreationRestricted = KaliumException.InvalidRequestError(
        ErrorResponse(403, "user creation restricted", "user-creation-restricted")
    )

    val tooManyTeamMembers = KaliumException.InvalidRequestError(
        ErrorResponse(403, "too many team members", "too-many-team-members")
    )

    val domainBlockedForRegistration = KaliumException.InvalidRequestError(
        ErrorResponse(451, "domain blocked for registration", "domain-blocked-for-registration")
    )

    val operationDenied = KaliumException.InvalidRequestError(
        ErrorResponse(403, "Insufficient permissions", "operation-denied")
    )

    val noTeam = KaliumException.InvalidRequestError(
        ErrorResponse(404, "Team not found", "no-team")
    )

    val noTeamMember = KaliumException.InvalidRequestError(
        ErrorResponse(403, "Not a team member", "no-team-member")
    )

    val noConversation = KaliumException.InvalidRequestError(
        ErrorResponse(404, "Conversation not found", "no-conversation")
    )

    val noConversationCode = KaliumException.InvalidRequestError(
        ErrorResponse(404, "Conversation code not found", "no-conversation-code")
    )

    val guestLinkDisables = KaliumException.InvalidRequestError(
        ErrorResponse(409, "Guest links are disabled", "guest-links-disabled")
    )

    val federationNotEnabled = KaliumException.FederationError(
        ErrorResponse(400, "no federator configured", "federation-not-enabled")
    )
}

object TestNetworkResponseError {
    fun noNetworkConnection(): NetworkFailure = NetworkFailure.NoNetworkConnection(null)
    fun <T : Any> genericResponseError(): NetworkResponse<T> = NetworkResponse.Error(TestNetworkException.generic)
}

fun serverMiscommunicationFailure(code: Int = HttpStatusCode.BadRequest.value, message: String = "", label: String = "") =
    NetworkFailure.ServerMiscommunication(KaliumException.InvalidRequestError(ErrorResponse(code, message, label)))
