package com.wire.kalium.network.utils

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.NetworkErrorLabel

object CustomErrors {
    val MISSING_REFRESH_TOKEN =
        NetworkResponse.Error(
            KaliumException.ServerError(
                ErrorResponse(
                    500,
                    "no cookie was found",
                    NetworkErrorLabel.KaliumCustom.MISSING_REFRESH_TOKEN
                )
            )
        )
}
