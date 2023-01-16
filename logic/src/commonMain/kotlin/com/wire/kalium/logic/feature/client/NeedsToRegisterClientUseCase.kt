package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will return true if the current user needs to register a client.
 */
interface NeedsToRegisterClientUseCase {
    suspend operator fun invoke(): Boolean
}

class NeedsToRegisterClientUseCaseImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : NeedsToRegisterClientUseCase {
    override suspend fun invoke(): Boolean = withContext(dispatcher.default) {
        sessionRepository.userAccountInfo(selfUserId).fold(
            { false },
            {
                when (it) {
                    is AccountInfo.Invalid -> false
                    is AccountInfo.Valid -> currentClientIdProvider().fold({ true }, { false })
                }
            }
        )
    }
}
