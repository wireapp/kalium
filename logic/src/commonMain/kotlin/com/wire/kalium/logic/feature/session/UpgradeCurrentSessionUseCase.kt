package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager

interface UpgradeCurrentSessionUseCase {
    suspend operator fun invoke(clientId: ClientId)
}

class UpgradeCurrentSessionUseCaseImpl(
    val accessTokenApi: AccessTokenApi,
    val sessionManager: SessionManager
): UpgradeCurrentSessionUseCase {

    override suspend operator fun invoke(clientId: ClientId) {
        wrapApiRequest {
            accessTokenApi.getToken(sessionManager.session().first.refreshToken, clientId.value)
        }.map {
            sessionManager.updateLoginSession(it.first, it.second)
        }

    }

}
