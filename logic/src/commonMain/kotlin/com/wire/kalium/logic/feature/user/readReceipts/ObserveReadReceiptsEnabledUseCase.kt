package com.wire.kalium.logic.feature.user.readReceipts

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


/**
 * UseCase that allow us to get the configuration of read receipts enabled or not
 */
interface ObserveReadReceiptsEnabledUseCase {
    operator fun invoke(): Flow<Boolean>
}

internal class ObserveReadReceiptsEnabledUseCaseImpl(
    val userConfigRepository: UserConfigRepository,
) : ObserveReadReceiptsEnabledUseCase {

    override fun invoke(): Flow<Boolean> {
        return userConfigRepository.isReadReceiptsEnabled().map { result ->
            result.fold({
                true
            }, {
                it
            })
        }
    }

}
