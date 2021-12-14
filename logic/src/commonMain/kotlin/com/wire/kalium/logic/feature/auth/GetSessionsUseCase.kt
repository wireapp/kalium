package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.session.SessionRepository

class GetSessionsUseCase(private val sessionRepository: SessionRepository) {

    suspend operator fun invoke(): List<AuthSession> = sessionRepository.getSessions() //TODO Any kind of failure possible?

}
