package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

class SelfServerConfigUseCase(
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId
) {
    suspend operator fun invoke(): Result =
        sessionRepository.userSession(selfUserId).fold({
            Result.Failure(it)
        }, {
            Result.Success(it.serverLinks)
        })

    sealed class Result {
        data class Success(val serverLinks: ServerConfig.Links) : Result()
        data class Failure(val cause: CoreFailure) : Result()
    }
}
