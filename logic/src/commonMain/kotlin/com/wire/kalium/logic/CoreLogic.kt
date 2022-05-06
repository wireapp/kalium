package com.wire.kalium.logic

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

expect class CoreLogic : CoreLogicCommon

abstract class CoreLogicCommon(
    // TODO: can client label be replaced with clientConfig.deviceName() ?
    protected val clientLabel: String,
    protected val rootPath: String,
    protected val idMapper: IdMapper = MapperProvider.idMapper()
) {

    val sessionRepository: SessionRepository by lazy {
        getSessionRepo()
    }

    protected abstract fun getSessionRepo(): SessionRepository

    protected abstract val globalPreferences: KaliumPreferences
    protected abstract val globalDatabase: GlobalDatabaseProvider

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    fun getAuthenticationScope(): AuthenticationScope =
        AuthenticationScope(clientLabel, sessionRepository, globalDatabase, globalPreferences)

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getSessionScope(userId: UserId): UserSessionScope

    suspend fun <T> authenticationScope(action: suspend AuthenticationScope.() -> T)
            : T = getAuthenticationScope().action()

    suspend fun <T> sessionScope(userId: UserId, action: suspend UserSessionScope.() -> T)
            : T = getSessionScope(userId).action()

    protected abstract val globalCallManager: GlobalCallManager
}
