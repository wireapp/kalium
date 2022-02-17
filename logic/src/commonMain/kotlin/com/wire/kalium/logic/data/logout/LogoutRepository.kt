package com.wire.kalium.logic.data.logout

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.user.logout.LogoutApi
import com.wire.kalium.network.utils.NetworkResponse

interface LogoutRepository {
    fun clearUserDB()
    fun clearUserPreferences()
    fun deleteUserSession()
    suspend fun logout(): Either<CoreFailure, Unit>
}

class LogoutDataSource(
    private val logoutApi: LogoutApi,
    private val sessionRepository: SessionRepository,
    private val userId: UserId
): LogoutRepository {
    override fun clearUserDB() {
        // TODO("Not yet implemented")
    }

    override fun clearUserPreferences() {
        // TODO("Not yet implemented")
    }

    override fun deleteUserSession() {
        sessionRepository.deleteSession(userId.value)
    }

    override suspend fun logout(): Either<CoreFailure, Unit> {
        return when(val result = logoutApi.logout()) {
            is NetworkResponse.Success -> Either.Right(Unit)
            is NetworkResponse.Error -> Either.Left(CoreFailure.Unknown(result.kException))
        }
    }
}
