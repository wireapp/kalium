package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.call.CallApi

interface CallRepository {
    suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String>
    suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray>
}

internal class CallDataSource(
    private val callApi: CallApi
) : CallRepository {

    override suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String> = wrapApiRequest {
        callApi.getCallConfig(limit = limit)
    }

    override suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray> = wrapApiRequest {
        callApi.connectToSFT(url = url, data = data)
    }
}
