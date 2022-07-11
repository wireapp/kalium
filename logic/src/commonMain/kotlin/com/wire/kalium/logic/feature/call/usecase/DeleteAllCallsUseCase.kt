package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository

interface DeleteAllCallsUseCase {
    suspend operator fun invoke()
}

internal class DeleteAllCallsUseCaseImpl(
    private val callRepository: CallRepository
) : DeleteAllCallsUseCase {

    override suspend fun invoke() {
        callRepository.deleteAllCalls()
    }
}
