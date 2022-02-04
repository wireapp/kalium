package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either

interface ClientRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client>
    suspend fun persistClientId(clientId: ClientId): Either<CoreFailure, Unit>
    suspend fun currentClientId(): Either<CoreFailure, ClientId>
}
