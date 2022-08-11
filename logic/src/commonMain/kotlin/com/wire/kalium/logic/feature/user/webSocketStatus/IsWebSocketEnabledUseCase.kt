package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold

interface IsWebSocketEnabledUseCase {
    operator fun invoke(): Boolean
}

internal class IsWebSocketEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsWebSocketEnabledUseCase {

    override operator fun invoke(): Boolean =
        userConfigRepository.isWebSocketEnabled().fold({
            false
        }, {
            it
        })
}
