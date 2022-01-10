package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either

interface ClientRepository {
    suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client>
}
