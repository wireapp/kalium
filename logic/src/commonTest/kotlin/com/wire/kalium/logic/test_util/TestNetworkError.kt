package com.wire.kalium.logic.test_util

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException

object TestNetworkError {

    val generic = NetworkFailure.ServerMiscommunication(
        KaliumException.InvalidRequestError(
            ErrorResponse(
                400,
                message = "generic test error",
                label = "generic-test-error"
            )
        )
    )

    val tooManyClient = NetworkFailure.ServerMiscommunication(
        KaliumException.InvalidRequestError(
            ErrorResponse(
                403,
                message = "too many clients",
                label = "too-many-clients"
            )
        )
    )

    val missingAuth = NetworkFailure.ServerMiscommunication(
        KaliumException.InvalidRequestError(
            ErrorResponse(
                403,
                message = "missing auth",
                label = "missing-auth"
            )
        )
    )
}
