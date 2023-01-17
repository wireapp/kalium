package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Upgrade the current login session to be associated with self user's client ID
 */
interface UpgradeCurrentSessionUseCase {
    suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, Unit>
}

class UpgradeCurrentSessionUseCaseImpl(
    private val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    private val accessTokenApi: AccessTokenApi,
    private val sessionManager: SessionManager,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : UpgradeCurrentSessionUseCase {
    override suspend operator fun invoke(clientId: ClientId): Either<CoreFailure, Unit> = withContext(dispatchers.default) {
        wrapStorageRequest { sessionManager.session()?.refreshToken }
            .flatMap { refreshToken ->
                wrapApiRequest {
                    accessTokenApi.getToken(refreshToken, clientId.value)
                }.flatMap {
                    wrapStorageRequest { sessionManager.updateLoginSession(it.first, it.second) }
                }.map {
                    authenticatedNetworkContainer.clearCachedToken()
                }
            }
    }
}
