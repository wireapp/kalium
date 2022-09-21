package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.configuration.GlobalConfigRepository
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ObservePersistentWebSocketConnectionStatusUseCase {
    operator fun invoke(): Flow<Boolean>
}

internal class ObservePersistentWebSocketConnectionStatusUseCaseImpl(
    private val globalConfigRepository: GlobalConfigRepository
) : ObservePersistentWebSocketConnectionStatusUseCase {

    override operator fun invoke(): Flow<Boolean> =
        globalConfigRepository.isPersistentWebSocketConnectionEnabledFlow().map { isPersistetWebSocketEnabledFlow ->
            isPersistetWebSocketEnabledFlow.fold({
                false
            }, {
                it
            })
        }
}
