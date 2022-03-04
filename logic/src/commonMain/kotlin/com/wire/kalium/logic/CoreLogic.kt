package com.wire.kalium.logic

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.configuration.ServerConfigMapperImpl
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope

expect class CoreLogic : CoreLogicCommon

abstract class CoreLogicCommon(
    // TODO: can client label be replaced with clientConfig.deviceName() ?
    protected val clientLabel: String,
    protected val rootProteusDirectoryPath: String,
    kaliumLoggerConfig: KaliumLogger.Config
) {

    protected val userScopeStorage = hashMapOf<AuthSession, AuthenticatedDataSourceSet>()
    // TODO: - Update UserSession when token is refreshed
    //       - Delete UserSession and DataSourceSets when user logs-out

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getAuthenticationScope(): AuthenticationScope

    protected val serverConfigMapper: ServerConfigMapper get() = ServerConfigMapperImpl()
    protected val sessionMapper: SessionMapper get() = SessionMapperImpl(serverConfigMapper)

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getSessionScope(session: AuthSession): UserSessionScope

    suspend fun <T> authenticationScope(action: suspend AuthenticationScope.() -> T)
            : T = getAuthenticationScope().action()

    suspend fun <T> sessionScope(session: AuthSession, action: suspend UserSessionScope.() -> T)
            : T = getSessionScope(session).action()

    protected val kaliumLogger: KaliumLogger = KaliumLogger(initialConfig = kaliumLoggerConfig)
}
