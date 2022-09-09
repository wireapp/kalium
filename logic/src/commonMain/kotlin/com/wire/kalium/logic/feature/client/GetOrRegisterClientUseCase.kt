package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import kotlinx.coroutines.flow.firstOrNull

interface GetOrRegisterClientUseCase {
    suspend operator fun invoke(
        registerClientParam: RegisterClientUseCase.RegisterClientParam
    ): RegisterClientResult
}

class GetOrRegisterClientUseCaseImpl(
    private val observeCurrentClientId: ObserveCurrentClientIdUseCase,
    private val selfClients: SelfClientsUseCase,
    private val registerClient: RegisterClientUseCase,
    private val clearClientData: ClearClientDataUseCase,
    private val proteusClient: ProteusClient
) : GetOrRegisterClientUseCase {

    override suspend fun invoke(registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult {
        val currentClientId = observeCurrentClientId().firstOrNull()
        if (currentClientId != null) { // probably there was a soft logout, we still have client data stored locally
            val selfClientsResult = selfClients()
            if (selfClientsResult is SelfClientsResult.Failure) {
                return selfClientsResult.toRegisterClientResult()
            } else if (selfClientsResult is SelfClientsResult.Success) {
                val client = selfClientsResult.clients.firstOrNull { it.id == currentClientId }
                if (client != null) { // client stored locally is still valid
                    return RegisterClientResult.Success(client)
                } else { // client stored locally isn't valid anymore
                    clearClientData()
                    proteusClient.open()
                }
            }
        }
        return registerClient(registerClientParam)
    }

    private fun SelfClientsResult.Failure.toRegisterClientResult(): RegisterClientResult = when (this) {
        is SelfClientsResult.Failure.Generic -> RegisterClientResult.Failure.Generic(this.genericFailure)
    }
}
