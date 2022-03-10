package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository

class UpdateCurrentSessionUseCase(private val sessionRepository: SessionRepository) {
    operator fun invoke(userIdValue: String) = sessionRepository.updateCurrentSession(userIdValue)
}
