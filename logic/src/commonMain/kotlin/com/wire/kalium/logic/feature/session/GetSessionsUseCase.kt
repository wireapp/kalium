package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

class GetSessionsUseCase(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): GetAllSessionsResult = sessionRepository.allSessions().fold(
        {
            when (it) {
                StorageFailure.DataNotFound -> GetAllSessionsResult.Failure.NoSessionFound
                is StorageFailure.Generic -> GetAllSessionsResult.Failure.Generic(it)
            }
        }, {
            GetAllSessionsResult.Success(it)
        }
    )

    fun getUserSession(userId: UserId) =
        sessionRepository.userSession(userId)

    fun deleteInvalidSession(userId: UserId) {
        sessionRepository.userSession(userId).map {
            if (it.session is AuthSession.Session.Invalid)
                sessionRepository.deleteSession(userId)
        }
    }

}
