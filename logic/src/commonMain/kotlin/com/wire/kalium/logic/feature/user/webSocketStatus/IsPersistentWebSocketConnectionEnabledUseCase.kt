package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.fold

interface IsPersistentWebSocketConnectionEnabledUseCase {
    operator fun invoke(): Boolean
}

internal class IsPersistentWebSocketConnectionEnabledUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : IsPersistentWebSocketConnectionEnabledUseCase {

    override operator fun invoke(): Boolean =
        userConfigRepository.isPersistentWebSocketConnectionEnabled().fold({
            false
        }, {
            it
        })
}
