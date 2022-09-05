package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapLeft
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.dao.client.ClientDAO
import io.ktor.util.encodeBase64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.dao.client.Client as ClientEntity

interface ClientRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client>
    suspend fun registerMLSClient(clientId: ClientId, publicKey: ByteArray): Either<CoreFailure, Unit>
    suspend fun persistClientId(clientId: ClientId): Either<CoreFailure, Unit>
    suspend fun currentClientId(): Either<CoreFailure, ClientId>
    suspend fun observeCurrentClientId(): Flow<ClientId?>
    suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit>
    suspend fun selfListOfClients(): Either<NetworkFailure, List<Client>>
    suspend fun clientInfo(clientId: ClientId /* = com.wire.kalium.logic.data.id.PlainId */): Either<NetworkFailure, Client>
    suspend fun storeUserClientList(userId: UserId, clients: List<OtherUserClient>): Either<StorageFailure, Unit>
    suspend fun storeUserClientIdList(userId: UserId, clients: List<ClientId>): Either<StorageFailure, Unit>
    suspend fun registerToken(body: PushTokenBody): Either<NetworkFailure, Unit>
    suspend fun deregisterToken(token: String): Either<NetworkFailure, Unit>
    suspend fun getClientsByUserId(userId: UserId): Either<StorageFailure, List<OtherUserClient>>
}
@Suppress("INAPPLICABLE_JVM_NAME")
class ClientDataSource(
    private val clientRemoteRepository: ClientRemoteRepository,
    private val clientRegistrationStorage: ClientRegistrationStorage,
    private val clientDAO: ClientDAO,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : ClientRepository {
    override suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client> {
        return clientRemoteRepository.registerClient(param)
    }

    override suspend fun persistClientId(clientId: ClientId): Either<CoreFailure, Unit> =
        wrapStorageRequest { clientRegistrationStorage.setRegisteredClientId(clientId.value) }

    override suspend fun currentClientId(): Either<CoreFailure, ClientId> = wrapStorageRequest {
        clientRegistrationStorage.getRegisteredClientId()?.let { ClientId(it) }
    }.mapLeft {
        if (it is StorageFailure.DataNotFound) {
            CoreFailure.MissingClientRegistration
        } else {
            it
        }
    }

    override suspend fun observeCurrentClientId(): Flow<ClientId?> =
        clientRegistrationStorage.observeRegisteredClientId().map { rawClientId ->
            rawClientId?.let { ClientId(it) }
        }

    override suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit> {
        return clientRemoteRepository.deleteClient(param)
    }

    // TODO(self-device-list): after fetch save list of self client in the db
    override suspend fun selfListOfClients(): Either<NetworkFailure, List<Client>> {
        return clientRemoteRepository.fetchSelfUserClients()
    }

    override suspend fun clientInfo(clientId: ClientId): Either<NetworkFailure, Client> {
        return clientRemoteRepository.fetchClientInfo(clientId)
    }

    override suspend fun registerMLSClient(clientId: ClientId, publicKey: ByteArray): Either<CoreFailure, Unit> =
        clientRemoteRepository.registerMLSClient(clientId, publicKey.encodeBase64())

    override suspend fun storeUserClientIdList(userId: UserId, clients: List<ClientId>): Either<StorageFailure, Unit> =
        userMapper.toUserIdPersistence(userId).let { userEntity ->
            clients.map { ClientEntity(userEntity, it.value, null) }.let { clientEntityList ->
                wrapStorageRequest { clientDAO.insertClients(clientEntityList) }
            }
        }
    override suspend fun storeUserClientList(userId: UserId, clients: List<OtherUserClient>): Either<StorageFailure, Unit> =
        userMapper.toUserIdPersistence(userId).let { userEntity ->
            clients.map { ClientEntity(userEntity, it.id, it.deviceType.name) }.let { clientEntityList ->
                wrapStorageRequest { clientDAO.insertClients(clientEntityList) }
            }
        }

    override suspend fun registerToken(body: PushTokenBody): Either<NetworkFailure, Unit> = clientRemoteRepository.registerToken(body)
    override suspend fun deregisterToken(token: String): Either<NetworkFailure, Unit> = clientRemoteRepository.deregisterToken(token)

    override suspend fun getClientsByUserId(userId: UserId): Either<StorageFailure, List<OtherUserClient>> =
        wrapStorageRequest {
            clientDAO.getClientsOfUserByQualifiedID(idMapper.toDaoModel(userId))
        }.map { clientsList ->
            userMapper.fromOtherUsersClientsDTO(clientsList)
        }
}
