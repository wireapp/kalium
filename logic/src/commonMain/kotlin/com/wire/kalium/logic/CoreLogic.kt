package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.ClientConfig
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.feature.UserSessionScopeCommon
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.network.LoginNetworkContainer

expect class CoreLogic: CoreLogicCommon

abstract class CoreLogicCommon(
    // TODO: can client label be replaced with clientConfig.deviceName() ?
    protected val clientLabel: String,
    protected val rootProteusDirectoryPath: String,
) {

    protected val loginContainer: LoginNetworkContainer by lazy {
        LoginNetworkContainer()
    }

    protected val userScopeStorage = hashMapOf<AuthSession, AuthenticatedDataSourceSet>()
    // TODO: - Update UserSession when token is refreshed
    //       - Delete UserSession and DataSourceSets when user logs-out

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getAuthenticationScope(): AuthenticationScope

    protected abstract val clientConfig: ClientConfig

    protected val sessionMapper: SessionMapper get() = SessionMapperImpl()

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getSessionScope(session: AuthSession): UserSessionScopeCommon

    suspend fun <T> authenticationScope(action: suspend AuthenticationScope.() -> T)
            : T = getAuthenticationScope().action()

    suspend fun <T> sessionScope(session: AuthSession, action: suspend UserSessionScopeCommon.() -> T)
            : T = getSessionScope(session).action()
}
