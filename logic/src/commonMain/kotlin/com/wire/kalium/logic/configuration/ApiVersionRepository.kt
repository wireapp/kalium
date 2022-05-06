package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.utils.NetworkResponse

interface ApiVersionRepository {
    suspend fun fetchApiVersion(): Either<NetworkFailure, List<Int>> // TODO change to proper response
}

class ApiVersionRepositoryImpl(): ApiVersionRepository {

    override suspend fun fetchApiVersion(): Either<NetworkFailure, List<Int>> = wrapApiRequest {
        NetworkResponse.Success(listOf(0, 1, 2), mapOf(), 200)  // TODO change to proper implementation
    }

}
