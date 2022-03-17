package com.wire.kalium.network.utils

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException

object CustomErrors {
    val MISSING_REFRESH_TOKEN =
        NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "no cookie was found", "missing-refreshToken")))
}
