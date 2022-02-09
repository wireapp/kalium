package com.wire.kalium.logic.data.client.remote

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either


interface ClientRemoteDataSource {
    suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client>

    suspend fun deleteClient(param: DeleteClientParam): Either<CoreFailure, Unit>

    suspend fun fetchClientInfo(clientId: ClientId): Either<CoreFailure, Client>

    suspend fun fetchSelfUserClient(): Either<CoreFailure, List<Client>>
}
