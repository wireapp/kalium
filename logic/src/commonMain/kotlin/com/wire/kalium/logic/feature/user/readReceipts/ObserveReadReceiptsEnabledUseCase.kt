package com.wire.kalium.logic.feature.user.readReceipts

import com.wire.kalium.logic.data.properties.UserPropertyRepository
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
    val userPropertyRepository: UserPropertyRepository,
) : ObserveReadReceiptsEnabledUseCase {

    override suspend fun invoke(): Flow<Boolean> {
        return userPropertyRepository.observeReadReceiptsStatus().map { result ->
            result.fold({
                true
            }, {
                it
            })
        }
    }

}
