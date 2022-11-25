package com.wire.kalium.logic.feature.user.readReceipts

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold

/**
 * UseCase that allow us to get the configuration of read receipts enabled or not
 */
interface IsReadReceiptsEnabledUseCase {
    operator fun invoke(): Boolean
}

internal class IsReadReceiptsEnabledUseCaseImpl(
    val userConfigRepository: UserConfigRepository,
) : IsReadReceiptsEnabledUseCase {

    override fun invoke(): Boolean {
        return userConfigRepository.isReadReceiptsEnabled()
            .fold({ true }) { isEnabled ->
                isEnabled
            }
    }

}


