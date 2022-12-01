package com.wire.kalium.logic.feature.user.readReceipts

import com.wire.kalium.logic.data.properties.PropertiesRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


/**
 * UseCase that allow us to get the configuration of read receipts enabled or not
 */
interface ObserveReadReceiptsEnabledUseCase {
    suspend operator fun invoke(): Flow<Boolean>
}

internal class ObserveReadReceiptsEnabledUseCaseImpl(
    val propertiesRepository: PropertiesRepository,
) : ObserveReadReceiptsEnabledUseCase {

    override suspend fun invoke(): Flow<Boolean> {
        return propertiesRepository.observeReadReceiptsStatus().map { result ->
            result.fold({
                true
            }, {
                it
            })
        }
    }

}
