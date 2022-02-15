package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository

class UpdateCurrentSessionUseCase(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(userIdValue: String) = sessionRepository.updateCurrentSession(userIdValue)
}
