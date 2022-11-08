package com.wire.kalium.logic.network

import app.cash.sqldelight.internal.Atomic
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.nullableFold
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapStorageNullableRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.model.AccessTokenDTO
import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.base.model.RefreshTokenDTO
import com.wire.kalium.network.api.base.model.SessionDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.ProxyCredentialsStorage

@Suppress("LongParameterList")
class SessionManagerImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val userId: QualifiedID,
    private val tokenStorage: AuthTokenStorage,
    private val proxyCredentialsStorage: ProxyCredentialsStorage,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : SessionManager {

    private val session: Atomic<SessionDTO?> = Atomic(null)
    private var serverConfig: Atomic<ServerConfigDTO?> = Atomic(null)

    override fun session(): SessionDTO = session.get() ?: run {
        wrapStorageRequest { tokenStorage.getToken(idMapper.toDaoModel(userId)) }
            .map { sessionMapper.fromEntityToSessionDTO(it) }
            .onSuccess { session.set(it) }
        session.get()!!
    }

    override fun serverConfig(): ServerConfigDTO = serverConfig.get() ?: run {
        serverConfig.set(sessionRepository.fullAccountInfo(userId)
            .map { serverConfigMapper.toDTO(it.serverConfig) }
            .fold({ throw error("use serverConfig is missing or an error while reading local storage") }, { it })
        )
        serverConfig.get()!!
    }

    override fun updateLoginSession(newAccessTokeDTO: AccessTokenDTO, newRefreshTokenDTO: RefreshTokenDTO?): SessionDTO =
        wrapStorageRequest {
            tokenStorage.updateToken(
                userId = idMapper.toDaoModel(userId),
                accessToken = newAccessTokeDTO.value,
                tokenType = newAccessTokeDTO.tokenType,
                refreshToken = newRefreshTokenDTO?.value
            )
        }.fold({
            TODO("IMPORTANT! Not yet implemented")
        }, {
            sessionMapper.fromEntityToSessionDTO(it)
        })

    override suspend fun onSessionExpired() {
        sessionRepository.logout(userId, LogoutReason.SESSION_EXPIRED)
    }

    override suspend fun onClientRemoved() {
        sessionRepository.logout(userId, LogoutReason.REMOVED_CLIENT)
    }

    override fun proxyCredentials(): ProxyCredentialsDTO? =
        wrapStorageNullableRequest { proxyCredentialsStorage.fetch() }.nullableFold({
            null
        }, {
            if (it != null) {
                sessionMapper.fromEntityToProxyCredentialsDTO(it)
            } else {
                null
            }
        })
}
