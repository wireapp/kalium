package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for retrieving the current user's server configuration.
 */
class SelfServerConfigUseCase internal constructor(
    private val selfUserId: UserId,
    private val serverConfigRepository: ServerConfigRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * @return [ServerConfig] or [CoreFailure]
     */
    suspend operator fun invoke(): Result = withContext(dispatchers.default) {
        serverConfigRepository.configForUser(selfUserId).fold({
            Result.Failure(it)
        }, {
            Result.Success(it)
        })
    }

    sealed class Result {
        // TODO: rename serverLinks to serverConfig
        data class Success(val serverLinks: ServerConfig) : Result()
        data class Failure(val cause: CoreFailure) : Result()
    }
}
