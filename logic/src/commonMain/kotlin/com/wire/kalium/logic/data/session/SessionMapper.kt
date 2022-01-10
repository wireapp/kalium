package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.persistence.model.SessionDao

interface SessionMapper {
    fun fromPersistenceSession(sessionDao: SessionDao): AuthSession

    fun toPersistenceSession(authSession: AuthSession): SessionDao
}

internal class SessionMapperImpl: SessionMapper {
    override fun fromPersistenceSession(sessionDao: SessionDao): AuthSession  = AuthSession(
        userId = sessionDao.userId,
        accessToken = sessionDao.accessToken,
        refreshToken = sessionDao.refreshToken,
        tokenType = sessionDao.tokenType
    )

    override fun toPersistenceSession(authSession: AuthSession): SessionDao = SessionDao(
        userId = authSession.userId,
        accessToken = authSession.accessToken,
        refreshToken = authSession.refreshToken,
        tokenType = authSession.tokenType
    )

}
