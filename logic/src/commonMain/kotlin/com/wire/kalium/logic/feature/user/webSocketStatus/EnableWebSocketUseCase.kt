package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either

interface EnableWebSocketUseCase {
    operator fun invoke(enabled: Boolean): Either<StorageFailure, Unit>
}

class EnableWebSocketUseCaseImpl(
    private val userConfigRepository: UserConfigRepository
) : EnableWebSocketUseCase {
    override operator fun invoke(enabled: Boolean) = userConfigRepository.persistWebSocketStatus(enabled)
}
