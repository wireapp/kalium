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
        val result: RegisterClientResult? = observeCurrentClientId().firstOrNull()?.let { currentClientId ->
            when (val selfClientsResult = selfClients()) {
                is SelfClientsResult.Failure -> selfClientsResult.toRegisterClientResult()
                is SelfClientsResult.Success -> {
                    val client = selfClientsResult.clients.firstOrNull { it.id == currentClientId }
                    if (client != null) { // client stored locally is still valid
                        RegisterClientResult.Success(client)
                    } else { // client stored locally isn't valid anymore
                        clearClientData()
                        proteusClient.open()
                        null
                    }
                }
            }
        }
        return result ?: registerClient(registerClientParam)
    }

    private fun SelfClientsResult.Failure.toRegisterClientResult(): RegisterClientResult = when (this) {
        is SelfClientsResult.Failure.Generic -> RegisterClientResult.Failure.Generic(this.genericFailure)
    }
}
