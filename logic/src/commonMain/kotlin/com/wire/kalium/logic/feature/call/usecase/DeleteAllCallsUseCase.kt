package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository

/**
 * Delete All Calls Use Case
 *
 * This UseCase will only be used in the Debug menu, until we have a fully working calling that is handling all the edge cases.
 *
 * This UseCase will be deleted after the statement above is done. As we don't have any functionality that requires the deletion of calls.
 */
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
