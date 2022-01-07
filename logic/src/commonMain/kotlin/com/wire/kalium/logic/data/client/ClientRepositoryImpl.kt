package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.remote.ClientRemoteDataSource
import com.wire.kalium.logic.functional.Either

class ClientRepositoryImpl(
    private val clientRemoteDataSource: ClientRemoteDataSource
) : ClientRepository {
    override suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client> =
        clientRemoteDataSource.registerClient(param)
}
