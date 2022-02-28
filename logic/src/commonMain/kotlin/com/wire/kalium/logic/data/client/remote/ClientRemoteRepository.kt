package com.wire.kalium.logic.data.client.remote

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.failure.ClientFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMissingAuth
import com.wire.kalium.network.exceptions.isTooManyClients

interface ClientRemoteRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client>
    suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit>
    suspend fun fetchClientInfo(clientId: ClientId): Either<NetworkFailure, Client>
    suspend fun fetchSelfUserClients(): Either<NetworkFailure, List<Client>>
}

class ClientRemoteDataSource(
    private val clientApi: ClientApi,
    private val clientMapper: ClientMapper
) : ClientRemoteRepository {

    override suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client> =
        wrapApiRequest { clientApi.registerClient(clientMapper.toRegisterClientRequest(param)) }
            .fold({ networkFailure ->
                when (networkFailure.kaliumException) {
                    is KaliumException.InvalidRequestError -> {
                        if (networkFailure.kaliumException.isTooManyClients())
                            Either.Left(ClientFailure.TooManyClients)
                        else if (networkFailure.kaliumException.isMissingAuth())
                            Either.Left(ClientFailure.WrongPassword)
                        else
                            Either.Left(NetworkFailure.ServerMiscommunication(networkFailure.kaliumException))
                    }
                    else -> Either.Left(NetworkFailure.ServerMiscommunication(networkFailure.kaliumException))
                }
            }, { clientResponse ->
                Either.Right(clientMapper.fromClientResponse(clientResponse))
            })

    override suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit> =
        wrapApiRequest { clientApi.deleteClient(param.password, param.clientId.value) }

    override suspend fun fetchClientInfo(clientId: ClientId): Either<NetworkFailure, Client> =
        wrapApiRequest { clientApi.fetchClientInfo(clientId.value) }
            .map { clientResponse -> clientMapper.fromClientResponse(clientResponse) }

    override suspend fun fetchSelfUserClients(): Either<NetworkFailure, List<Client>> =
        wrapApiRequest { clientApi.fetchSelfUserClient() }
            .map { clientResponseList ->
                clientResponseList.map { clientMapper.fromClientResponse(it) }
            }
}
