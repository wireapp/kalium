package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.functional.suspending

interface NeedsToRegisterClientUseCase {
    suspend operator fun invoke(): Boolean
}

class NeedsToRegisterClientUseCaseImpl(private val clientRepository: ClientRepository) : NeedsToRegisterClientUseCase {
    override suspend fun invoke(): Boolean = suspending {
        clientRepository.currentClientId()
    }.fold({ true }, { false })
}
