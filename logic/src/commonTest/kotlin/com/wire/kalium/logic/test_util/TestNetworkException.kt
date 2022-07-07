package com.wire.kalium.logic.test_util

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.network.api.ErrorResponse
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

    val invalidCredentials = KaliumException.InvalidRequestError(
        ErrorResponse(403, message = "invalid credentials", label = "invalid-credentials")
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

}

object TestNetworkResponseError {
    fun noNetworkConnection(): NetworkFailure = NetworkFailure.NoNetworkConnection(null)
    fun <T : Any> genericResponseError(): NetworkResponse<T> = NetworkResponse.Error(TestNetworkException.generic)
}

fun serverMiscommunicationFailure(code: Int = HttpStatusCode.BadRequest.value, message: String = "", label: String = "") =
    NetworkFailure.ServerMiscommunication(KaliumException.InvalidRequestError(ErrorResponse(code, message, label)))
