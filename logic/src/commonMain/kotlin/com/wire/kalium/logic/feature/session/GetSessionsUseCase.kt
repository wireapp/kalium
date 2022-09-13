package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

class GetSessionsUseCase(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(): GetAllSessionsResult = sessionRepository.allSessions().fold(
        {
            when (it) {
                StorageFailure.DataNotFound -> GetAllSessionsResult.Failure.NoSessionFound
                is StorageFailure.Generic -> GetAllSessionsResult.Failure.Generic(it)
            }
        }, {
            GetAllSessionsResult.Success(it)
        }
    )

    suspend fun getUserSession(userId: UserId) =
        sessionRepository.userAccountInfo(userId)

    suspend fun deleteInvalidSession(userId: UserId) {
        sessionRepository.userAccountInfo(userId).map {
            if (it is AccountInfo.Invalid)
                sessionRepository.deleteSession(userId)
        }
    }

}
