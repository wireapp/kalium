package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.client.ClientRegistrationStorage

interface ClientRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client>
    suspend fun persistClientId(clientId: ClientId): Either<CoreFailure, Unit>
    suspend fun currentClientId(): Either<CoreFailure, ClientId>
    suspend fun deleteClient(param: DeleteClientParam): Either<CoreFailure, Unit>
    suspend fun selfListOfClients(): Either<CoreFailure, List<Client>>
    suspend fun clientInfo(clientId: ClientId /* = com.wire.kalium.logic.data.id.PlainId */): Either<CoreFailure, Client>
}


class ClientDataSource(
    private val clientRemoteRepository: ClientRemoteRepository,
    private val clientRegistrationStorage: ClientRegistrationStorage
) : ClientRepository {
    override suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client> {
        return clientRemoteRepository.registerClient(param)
    }

    override suspend fun persistClientId(clientId: ClientId): Either<CoreFailure, Unit> {
        clientRegistrationStorage.registeredClientId = clientId.value
        return Either.Right(Unit)
    }

    override suspend fun currentClientId(): Either<CoreFailure, ClientId> {
        return clientRegistrationStorage.registeredClientId?.let { clientId ->
            Either.Right(ClientId(clientId))
        } ?: Either.Left(CoreFailure.MissingClientRegistration)
    }

    override suspend fun deleteClient(param: DeleteClientParam): Either<CoreFailure, Unit> {
        return clientRemoteRepository.deleteClient(param)
    }

    override suspend fun selfListOfClients(): Either<CoreFailure, List<Client>> {
        return clientRemoteRepository.fetchSelfUserClients()
    }

    override suspend fun clientInfo(clientId: ClientId): Either<CoreFailure, Client> {
        return clientRemoteRepository.fetchClientInfo(clientId)
    }
}
