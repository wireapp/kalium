package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.ServerConfigMapper
import com.wire.kalium.logic.configuration.ServerConfigMapperImpl
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.session.SessionMapperImpl
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.network.api.NonQualifiedUserId

expect class CoreLogic : CoreLogicCommon

abstract class CoreLogicCommon(
    // TODO: can client label be replaced with clientConfig.deviceName() ?
    protected val clientLabel: String,
    protected val rootProteusDirectoryPath: String
) {

    val sessionRepository: SessionRepository by lazy {
        getSessionRepo()
    }
    protected abstract fun getSessionRepo(): SessionRepository


    protected val userScopeStorage = hashMapOf<NonQualifiedUserId, AuthenticatedDataSourceSet>()
    //  TODO:     - Delete UserSession and DataSourceSets when user logs-out

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getAuthenticationScope(): AuthenticationScope

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getSessionScope(userId: NonQualifiedUserId): UserSessionScope

    suspend fun <T> authenticationScope(action: suspend AuthenticationScope.() -> T)
            : T = getAuthenticationScope().action()

    suspend fun <T> sessionScope(userId: NonQualifiedUserId, action: suspend UserSessionScope.() -> T)
            : T = getSessionScope(userId).action()
}
