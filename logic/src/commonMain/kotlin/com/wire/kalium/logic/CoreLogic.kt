package com.wire.kalium.logic

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.UpdateApiVersionsScheduler
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

    protected abstract val globalPreferences: Lazy<KaliumPreferences>
    protected abstract val globalDatabase: Lazy<GlobalDatabaseProvider>

    fun getGlobalScope(): KaliumScope = KaliumScope(globalDatabase, globalPreferences, sessionRepository)

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    fun getAuthenticationScope(backendLinks: ServerConfig.Links): AuthenticationScope =
        // TODO(logic): make it lazier
        AuthenticationScope(clientLabel, globalPreferences.value, globalDatabase.value, backendLinks)

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    abstract fun getSessionScope(userId: UserId): UserSessionScope


    inline fun <T> globalScope(action: KaliumScope.() -> T)
            : T = getGlobalScope().action()

    inline fun <T> authenticationScope(backendLinks: ServerConfig.Links, action: AuthenticationScope.() -> T)
            : T = getAuthenticationScope(backendLinks).action()

    inline fun <T> sessionScope(userId: UserId, action: UserSessionScope.() -> T)
            : T = getSessionScope(userId).action()

    protected abstract val globalCallManager: GlobalCallManager

    protected abstract val globalWorkScheduler: GlobalWorkScheduler

    val updateApiVersionsScheduler: UpdateApiVersionsScheduler get() = globalWorkScheduler
}
