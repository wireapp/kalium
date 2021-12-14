package com.wire.kalium.network.exceptions

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.message.SendMessageResponse

sealed class KaliumException(val errorCode: Int) : Exception() {
    class RedirectError(val errorResponse: ErrorResponse) : KaliumException(errorCode = errorResponse.code)
    class InvalidRequestError(val errorResponse: ErrorResponse) : KaliumException(errorCode = errorResponse.code)
    class ServerError(val errorResponse: ErrorResponse) : KaliumException(errorCode = errorResponse.code)
    class GenericError(val errorResponse: ErrorResponse?) : KaliumException(errorCode = errorResponse?.code ?: 400)
    class NetworkUnavailableError(val errorResponse: ErrorResponse?, cause: Throwable?) : KaliumException(errorCode = errorResponse?.code ?: 400)
    sealed class FeatureError(errorCode: Int) : KaliumException(errorCode)
}

sealed class SentMessageError(errorCode: Int) : KaliumException.FeatureError(errorCode) {
    class MissingDeviceError(val errorBody: SendMessageResponse.MissingDevicesResponse, errorCode: Int) : SentMessageError(errorCode)
}
