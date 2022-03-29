package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.suspending

interface CurrentClientIdUseCase {
    suspend operator fun invoke(): CurrentClientIdResult
}

class CurrentClientIdUseCaseImpl(private val clientRepository: ClientRepository) : CurrentClientIdUseCase {
    override suspend fun invoke(): CurrentClientIdResult = suspending {
        clientRepository.currentClientId()
    }.fold(
        {
            CurrentClientIdResult.MissingClientRegistration
        }, {
            CurrentClientIdResult.Success(it)
        }
    )
}

sealed class CurrentClientIdResult {
    data class Success(val clientId: ClientId) : CurrentClientIdResult()
    object MissingClientRegistration: CurrentClientIdResult()
}
