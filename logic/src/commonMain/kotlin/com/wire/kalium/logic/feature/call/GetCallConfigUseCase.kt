package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger

interface GetCallConfigUseCase {
    suspend operator fun invoke(limit: Int?): String
}

class GetCallConfigUseCaseImpl(
    private val callRepository: CallRepository
) : GetCallConfigUseCase {

    override suspend fun invoke(limit: Int?): String =
        when (val result = callRepository.getCallConfigResponse(limit = limit)) {
            is Either.Left -> {
                kaliumLogger.e("GetCallConfigUseCaseImpl - Call Config Error")
                throw Throwable("call config error")
            }
            is Either.Right -> result.value
        }
}
