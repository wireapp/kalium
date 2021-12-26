package com.wire.kalium.logic.data.client.remote

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.functional.Either


interface ClientRemoteDataSource {
    suspend fun registerClient(param: RegisterClientParam): Either<CoreFailure, Client>

    }
