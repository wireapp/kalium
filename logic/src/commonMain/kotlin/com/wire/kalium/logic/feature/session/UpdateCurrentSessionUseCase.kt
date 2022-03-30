package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId

class UpdateCurrentSessionUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(userId: UserId) = sessionRepository.updateCurrentSession(userId)
}
