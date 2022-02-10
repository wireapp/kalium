package com.wire.kalium.logic.data.client.remote

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.failure.ClientFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful


interface ClientRemoteRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client>

    suspend fun deleteClient(param: DeleteClientParam): Either<CoreFailure, Unit>

    suspend fun fetchClientInfo(clientId: ClientId): Either<CoreFailure, Client>

    suspend fun fetchSelfUserClient(): Either<CoreFailure, List<Client>>
}

class ClientRemoteDataSource(
    private val clientApi: ClientApi,
    private val clientMapper: ClientMapper
) : ClientRemoteRepository {

    override suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client> {
        val response = clientApi.registerClient(clientMapper.toRegisterClientRequest(param))
        return if (response.isSuccessful())
            Either.Right(clientMapper.fromClientResponse(response = response.value))
        else
            handleFailedApiResponse(response)
    }

    override suspend fun deleteClient(param: DeleteClientParam): Either<CoreFailure, Unit> {
        val response = clientApi.deleteClient(param.password, param.clientId.value)
        return if (response.isSuccessful()) {
            Either.Right(Unit)
        } else {
            handleFailedApiResponse(response)
        }
    }

    override suspend fun fetchClientInfo(clientId: ClientId): Either<CoreFailure, Client> {
        val response = clientApi.fetchClientInfo(clientId.value)
        return if (response.isSuccessful()) {
            Either.Right(clientMapper.fromClientResponse(response.value))
        } else {
            handleFailedApiResponse(response)
        }
    }

    override suspend fun fetchSelfUserClient(): Either<CoreFailure, List<Client>> {
        val response = clientApi.fetchSelfUserClient()
        return if (response.isSuccessful()) {
            Either.Right(response.value.map { clientMapper.fromClientResponse(it) })
        } else {
            handleFailedApiResponse(response)
        }
    }


    private fun handleFailedApiResponse(response: NetworkResponse.Error<*>): Either.Left<CoreFailure> =
        when (response.kException) {
            is KaliumException.InvalidRequestError ->
                when (response.kException.message) {
                    ERROR_MESSAGE_TOO_MANY_CLIENTS -> Either.Left(ClientFailure.TooManyClients)
                    ERROR_MESSAGE_MISSING_AUTH -> Either.Left(ClientFailure.WrongPassword)
                    else -> Either.Left(CoreFailure.Unknown(response.kException))
                }
            is KaliumException.NetworkUnavailableError -> Either.Left(CoreFailure.NoNetworkConnection)
            else -> Either.Left(CoreFailure.Unknown(response.kException))
        }

    private companion object {
        private const val ERROR_MESSAGE_TOO_MANY_CLIENTS = "too-many-clients"
        private const val ERROR_MESSAGE_MISSING_AUTH = "missing-auth"
    }
}
