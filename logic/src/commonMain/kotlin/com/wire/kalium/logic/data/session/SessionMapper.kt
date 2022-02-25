package com.wire.kalium.logic.data.session

import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.network.api.SessionCredentials
import com.wire.kalium.persistence.model.PersistenceSession

interface SessionMapper {
    fun toSessionCredentials(authSession: AuthSession): SessionCredentials

    fun fromPersistenceSession(persistenceSession: PersistenceSession): AuthSession
    fun toPersistenceSession(authSession: AuthSession): PersistenceSession
}

internal class SessionMapperImpl(private val serverConfigMapper: ServerConfigMapper) : SessionMapper {

    override fun toSessionCredentials(authSession: AuthSession): SessionCredentials = SessionCredentials(
        tokenType = authSession.tokenType,
        accessToken = authSession.accessToken,
        refreshToken = authSession.refreshToken
    )

    override fun fromPersistenceSession(persistenceSession: PersistenceSession): AuthSession = AuthSession(
        userId = persistenceSession.userId,
        accessToken = persistenceSession.accessToken,
        refreshToken = persistenceSession.refreshToken,
        tokenType = persistenceSession.tokenType,
        serverConfig = serverConfigMapper.fromNetworkConfig(persistenceSession.networkConfig)
    )

    override fun toPersistenceSession(authSession: AuthSession): PersistenceSession = PersistenceSession(
        userId = authSession.userId,
        accessToken = authSession.accessToken,
        refreshToken = authSession.refreshToken,
        tokenType = authSession.tokenType,
        networkConfig = serverConfigMapper.toNetworkConfig(authSession.serverConfig)
    )

}
