package com.wire.kalium.logic.data.client.remote

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.failure.WrongPassword
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.ClientResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful

class ClientRemoteDataSourceImpl(
    private val clientApi: ClientApi,
    private val clientMapper: ClientMapper
): ClientRemoteDataSource {
    override suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client> {
        val response = clientApi.registerClient(clientMapper.toRegisterClientRequest(param))
        return if (response.isSuccessful())
            handleSuccessfulApiResponse(response)
        else
            handleFailedApiResponse(response)
    }

    private fun handleSuccessfulApiResponse(response: NetworkResponse.Success<ClientResponse>): Either<CoreFailure, Client> {
        return Either.Right(clientMapper.fromClientResponse(response = response.value))
    }

    private fun handleFailedApiResponse(response: NetworkResponse.Error<*>) =
        when (response.kException) {
            is KaliumException.InvalidRequestError -> Either.Left(WrongPassword)
            is KaliumException.NetworkUnavailableError -> Either.Left(CoreFailure.NoNetworkConnection)
            else -> Either.Left(CoreFailure.Unknown(response.kException))
        }
}
