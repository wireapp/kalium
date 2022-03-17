package com.wire.kalium.logic.test_util

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse

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

    val invalidCode = KaliumException.InvalidRequestError(
        ErrorResponse(404, message = "invalid code", label = "invalid-code")
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

}

object TestNetworkResponseError{

    fun <T : Any>genericError() : NetworkResponse<T> = NetworkResponse.Error(TestNetworkException.generic)

}
