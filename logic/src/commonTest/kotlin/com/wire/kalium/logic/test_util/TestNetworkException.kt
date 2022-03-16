package com.wire.kalium.logic.test_util

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException

object TestNetworkException {

    val generic = KaliumException.InvalidRequestError(
        ErrorResponse(
            400,
            message = "generic test error",
            label = "generic-test-error"
        )
    )

    val tooManyClient = KaliumException.InvalidRequestError(
        ErrorResponse(
            403,
            message = "too many clients",
            label = "too-many-clients"
        )
    )

    val missingAuth = KaliumException.InvalidRequestError(
        ErrorResponse(
            403,
            message = "missing auth",
            label = "missing-auth"
        )
    )

    val invalidCredentials = KaliumException.InvalidRequestError(
        ErrorResponse(
            403,
            message = "invalid credentials",
            label = "invalid-credentials"
        )
    )

    val invalidCode = KaliumException.InvalidRequestError(
        ErrorResponse(
            404,
            message = "invalid code",
            label = "invalid-code"
        )
    )
}
