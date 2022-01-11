package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.SessionCredentials
import com.wire.kalium.persistence.model.SessionDao

interface SessionMapper {
    fun toSessionCredentials(authSession: AuthSession): SessionCredentials

    fun fromSessionDao(sessionDao: SessionDao): AuthSession
    fun toSessionDao(authSession: AuthSession): SessionDao
}

internal class SessionMapperImpl : SessionMapper {

    override fun toSessionCredentials(authSession: AuthSession): SessionCredentials = SessionCredentials(
        tokenType = authSession.tokenType,
        accessToken = authSession.accessToken,
        refreshToken = authSession.refreshToken
    )

    override fun fromSessionDao(sessionDao: SessionDao): AuthSession = AuthSession(
        userId = sessionDao.userId,
        accessToken = sessionDao.accessToken,
        refreshToken = sessionDao.refreshToken,
        tokenType = sessionDao.tokenType
    )

    override fun toSessionDao(authSession: AuthSession): SessionDao = SessionDao(
        userId = authSession.userId,
        accessToken = authSession.accessToken,
        refreshToken = authSession.refreshToken,
        tokenType = authSession.tokenType
    )

}
