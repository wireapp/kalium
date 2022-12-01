package com.wire.kalium.logic.data.properties

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import kotlinx.coroutines.flow.Flow

interface PropertiesRepository {
    suspend fun observeReadReceiptsStatus(): Flow<Either<CoreFailure, Boolean>>
    suspend fun setReadReceiptsEnabled(): Either<CoreFailure, Unit>
    suspend fun deleteReadReceiptsProperty(): Either<CoreFailure, Unit>
}

internal class PropertiesDataSource(
    private val propertiesApi: PropertiesApi,
    private val userConfigRepository: UserConfigRepository,
) : PropertiesRepository {

    override suspend fun observeReadReceiptsStatus(): Flow<Either<CoreFailure, Boolean>> = userConfigRepository.isReadReceiptsEnabled()


    override suspend fun setReadReceiptsEnabled(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.setProperty(PropertiesApi.PropertyKey.WIRE_RECEIPT_MODE, 1)
    }.flatMap {
        userConfigRepository.setReadReceiptsStatus(true)
    }

    override suspend fun deleteReadReceiptsProperty(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.deleteProperty(PropertiesApi.PropertyKey.WIRE_RECEIPT_MODE)
    }.flatMap {
        userConfigRepository.setReadReceiptsStatus(false)
    }

}
