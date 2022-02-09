package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.client.ClientRegistrationStorage

class ClientRepositoryImpl(
    private val clientRemoteDataSource: ClientRemoteDataSource,
    private val clientRegistrationStorage: ClientRegistrationStorage
) : ClientRepository {
    override suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client> =
        clientRemoteDataSource.registerClient(param)


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
        return clientRemoteDataSource.deleteClient(param)
    }

    override suspend fun selfListOfClients(): Either<CoreFailure, List<Client>> {
        return clientRemoteDataSource.fetchSelfUserClient()
    }

    override suspend fun clientInfo(clientId: ClientId): Either<CoreFailure, Client> {
        return clientRemoteDataSource.fetchClientInfo(clientId)
    }


}
