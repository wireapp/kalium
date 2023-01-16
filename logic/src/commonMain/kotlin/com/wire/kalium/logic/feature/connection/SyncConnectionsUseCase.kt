package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

internal interface SyncConnectionsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class SyncConnectionsUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SyncConnectionsUseCase {

    override suspend fun invoke(): Either<CoreFailure, Unit> = withContext(dispatcher.default) {
        connectionRepository.fetchSelfUserConnections()
    }
}
