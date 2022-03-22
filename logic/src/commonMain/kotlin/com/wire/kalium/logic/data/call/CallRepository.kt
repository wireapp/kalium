package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.call.CallApi

interface CallRepository {
    suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String>
}

internal class CallDataSource(
    private val callApi: CallApi
) : CallRepository {

    override suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String> = suspending {
        wrapApiRequest {
            callApi.getCallConfig(limit = limit)
        }
    }
}
