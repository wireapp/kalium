package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

interface GetPersistentWebSocketStatus {
    suspend operator fun invoke(): Boolean
}

internal class GetPersistentWebSocketStatusImpl(
    private val userId: UserId,
    private val sessionRepository: SessionRepository
) : GetPersistentWebSocketStatus {
    override suspend operator fun invoke(): Boolean =
        sessionRepository.persistentWebSocketStatus(userId).fold({
            false
        }, {
            it
        })
}
