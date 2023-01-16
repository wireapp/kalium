package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Use Case that listen to any user connection changes
 */
fun interface MarkConnectionRequestAsNotifiedUseCase {
    /**
     * Use case [MarkConnectionRequestAsNotifiedUseCase] operation
     *
     * marks specificConnectionRequest as notified, so user should not receive notification about it anymore
     * @param userId UserId ConnectionRequest from which should be marker, if null - mark all ConnectionRequests as notified
     */
    suspend operator fun invoke(userId: UserId?)
}

internal class MarkConnectionRequestAsNotifiedUseCaseImpl(
    private val connectionRepository: ConnectionRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : MarkConnectionRequestAsNotifiedUseCase {

    override suspend operator fun invoke(userId: UserId?) = withContext(dispatcher.default) {
        if (userId == null) {
            connectionRepository.setAllConnectionsAsNotified()
        } else {
            connectionRepository.setConnectionAsNotified(userId)
        }
    }
}
