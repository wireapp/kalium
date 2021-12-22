package com.wire.kalium.logic.feature.auth

import com.wire.kalium.network.api.SessionCredentials

class AuthSessionMapper {

    fun toSessionCredentials(authSession: AuthSession): SessionCredentials = SessionCredentials(
        tokenType = authSession.tokenType,
        accessToken = authSession.accessToken,
        refreshToken = authSession.refreshToken
    )
}
