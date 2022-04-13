package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.functional.Either

internal interface SyncConnectionsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class SyncConnectionsUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : SyncConnectionsUseCase {

    override suspend fun invoke(): Either<CoreFailure, Unit> {

        return connectionRepository.fetchSelfUserConnections()
    }
}
