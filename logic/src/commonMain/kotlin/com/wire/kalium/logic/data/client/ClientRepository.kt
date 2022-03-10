package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.Client as ClientEntity

interface ClientRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client>
    suspend fun persistClientId(clientId: ClientId): Either<CoreFailure, Unit>
    suspend fun currentClientId(): Either<CoreFailure, ClientId>
    suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit>
    suspend fun selfListOfClients(): Either<NetworkFailure, List<Client>>
    suspend fun clientInfo(clientId: ClientId /* = com.wire.kalium.logic.data.id.PlainId */): Either<NetworkFailure, Client>
    suspend fun saveNewClients(userId: UserId, clients: List<ClientId>): Either<CoreFailure, Unit>
}


class ClientDataSource(
    private val clientRemoteRepository: ClientRemoteRepository,
    private val clientRegistrationStorage: ClientRegistrationStorage,
    private val clientDAO: ClientDAO,
    private val userMapper: UserMapper
) : ClientRepository {
    override suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client> {
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

    override suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit> {
        return clientRemoteRepository.deleteClient(param)
    }

    // TODO: after fetch save list of self client in the db
    override suspend fun selfListOfClients(): Either<NetworkFailure, List<Client>> {
        return clientRemoteRepository.fetchSelfUserClients()
    }

    override suspend fun clientInfo(clientId: ClientId): Either<NetworkFailure, Client> {
        return clientRemoteRepository.fetchClientInfo(clientId)
    }

    override suspend fun saveNewClients(userId: UserId, clients: List<ClientId>) : Either<CoreFailure, Unit> {
        val mappedUserId = userMapper.toUserIdPersistence(userId)
        val mappedClients = clients.map { ClientEntity(mappedUserId, it.value) }
        clientDAO.insertClients(mappedClients)
        return Either.Right(Unit)
    }
}
