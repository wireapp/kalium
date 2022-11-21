package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapLeft
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.model.PushTokenBody
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.InsertClientParam
import io.ktor.util.encodeBase64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.dao.client.Client as ClientEntity

@Suppress("TooManyFunctions")
interface ClientRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client>
    suspend fun registerMLSClient(clientId: ClientId, publicKey: ByteArray): Either<CoreFailure, Unit>
    suspend fun hasRegisteredMLSClient(): Either<CoreFailure, Boolean>
    suspend fun persistClientId(clientId: ClientId): Either<CoreFailure, Unit>

    @Deprecated("this function is not cached use CurrentClientIdProvider")
    suspend fun currentClientId(): Either<CoreFailure, ClientId>
    suspend fun clearCurrentClientId(): Either<CoreFailure, Unit>
    suspend fun retainedClientId(): Either<CoreFailure, ClientId>
    suspend fun clearRetainedClientId(): Either<CoreFailure, Unit>
    suspend fun clearHasRegisteredMLSClient(): Either<CoreFailure, Unit>
    suspend fun observeCurrentClientId(): Flow<ClientId?>
    suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit>
    suspend fun selfListOfClients(): Either<NetworkFailure, List<Client>>
    suspend fun clientInfo(clientId: ClientId /* = com.wire.kalium.logic.data.id.PlainId */): Either<NetworkFailure, Client>
    suspend fun storeUserClientListAndRemoveRedundantClients(userId: UserId, clients: List<OtherUserClient>): Either<StorageFailure, Unit>
    suspend fun storeUserClientIdList(userId: UserId, clients: List<ClientId>): Either<StorageFailure, Unit>
    suspend fun registerToken(body: PushTokenBody): Either<NetworkFailure, Unit>
    suspend fun deregisterToken(token: String): Either<NetworkFailure, Unit>
    suspend fun getClientsByUserId(userId: UserId): Either<StorageFailure, List<OtherUserClient>>
    suspend fun tryMarkingAsInvalid(userId: UserId, clientId: ClientId): Either<StorageFailure, Unit>
}

@Suppress("TooManyFunctions", "INAPPLICABLE_JVM_NAME", "LongParameterList")
class ClientDataSource(
    private val clientRemoteRepository: ClientRemoteRepository,
    private val clientRegistrationStorage: ClientRegistrationStorage,
    private val clientDAO: ClientDAO,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val clientMapper: ClientMapper = MapperProvider.clientMapper()
) : ClientRepository {
    override suspend fun registerClient(param: RegisterClientParam): Either<NetworkFailure, Client> {
        return clientRemoteRepository.registerClient(param)
    }

    override suspend fun persistClientId(clientId: ClientId): Either<CoreFailure, Unit> =
        wrapStorageRequest { clientRegistrationStorage.setRegisteredClientId(clientId.value) }

    override suspend fun clearCurrentClientId(): Either<CoreFailure, Unit> =
        wrapStorageRequest { clientRegistrationStorage.clearRegisteredClientId() }

    override suspend fun clearRetainedClientId(): Either<CoreFailure, Unit> =
        wrapStorageRequest { clientRegistrationStorage.clearRetainedClientId() }

    override suspend fun clearHasRegisteredMLSClient(): Either<CoreFailure, Unit> =
        wrapStorageRequest { clientRegistrationStorage.clearHasRegisteredMLSClient() }

    override suspend fun currentClientId(): Either<CoreFailure, ClientId> =
        wrapStorageRequest { clientRegistrationStorage.getRegisteredClientId() }
            .map { ClientId(it) }
            .mapLeft {
                if (it is StorageFailure.DataNotFound) {
                    kaliumLogger.e("Data Not Found for Registered Client Id")
                    CoreFailure.MissingClientRegistration
                } else {
                    kaliumLogger.e("Failure when getting Registered Client Id")
                    it
                }
            }

    override suspend fun retainedClientId(): Either<CoreFailure, ClientId> =
        wrapStorageRequest { clientRegistrationStorage.getRetainedClientId() }
            .map { ClientId(it) }
            .mapLeft {
                if (it is StorageFailure.DataNotFound) {
                    kaliumLogger.e("Data Not Found for Retained Client Id")
                    CoreFailure.MissingClientRegistration
                } else {
                    kaliumLogger.e("Failure when getting Retained Client Id")
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
            .flatMap {
                wrapStorageRequest {
                    clientRegistrationStorage.setHasRegisteredMLSClient()
                }
            }

    override suspend fun hasRegisteredMLSClient(): Either<CoreFailure, Boolean> =
        wrapStorageRequest {
            clientRegistrationStorage.hasRegisteredMLSClient()
        }

    override suspend fun storeUserClientIdList(userId: UserId, clients: List<ClientId>): Either<StorageFailure, Unit> =
        userMapper.toUserIdPersistence(userId).let { userEntity ->
            clients.map { InsertClientParam(userEntity, it.value, null) }.let { clientEntityList ->
                wrapStorageRequest { clientDAO.insertClients(clientEntityList) }
            }
        }

    override suspend fun storeUserClientListAndRemoveRedundantClients(
        userId: UserId,
        clients: List<OtherUserClient>
    ): Either<StorageFailure, Unit> =
        userMapper.toUserIdPersistence(userId).let { userEntity ->
            clients.map { InsertClientParam(userEntity, it.id, clientMapper.toDeviceTypeEntity(it.deviceType)) }.let { clientEntityList ->
                wrapStorageRequest { clientDAO.insertClientsAndRemoveRedundant(userEntity, clientEntityList) }
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

    override suspend fun tryMarkingAsInvalid(userId: UserId, clientId: ClientId) =
        wrapStorageRequest { clientDAO.tryMarkInvalid(idMapper.toDaoModel(userId), clientId.value) }
}
