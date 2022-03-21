package com.wire.kalium.logic.network

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.network.api.NonQualifiedUserId
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.session.UserSessionManager
import com.wire.kalium.network.tools.BackendConfig

internal class UserSessionManagerImpl(
    private val sessionRepository: SessionRepository,
    private val userId: NonQualifiedUserId
    ): UserSessionManager {
    override fun userConfig(): Pair<SessionDTO, BackendConfig> {
        TODO("Not yet implemented")
    }

    override fun updateSession(accessToken: AccessTokenDTO, refreshTokenDTO: RefreshTokenDTO?): SessionDTO {
        TODO("Not yet implemented")
    }
}
