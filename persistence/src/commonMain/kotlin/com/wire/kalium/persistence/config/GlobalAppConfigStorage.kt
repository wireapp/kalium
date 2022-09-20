package com.wire.kalium.persistence.config

import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface GlobalAppConfigStorage {

    /**
     * save flag from the user settings to enable and disable the logging
     */
    fun enableLogging(enabled: Boolean)

    /**
     * get the saved flag to know if the logging enabled or not
     */
    fun isLoggingEnables(): Boolean

    /**
     * save flag from the user settings to enable and disable the persistent webSocket connection
     */
    fun persistPersistentWebSocketConnectionStatus(enabled: Boolean)

    /**
     * get the saved flag to know if the persistent webSocket connection enabled or not
     */
    fun isPersistentWebSocketConnectionEnabledFlow(): Flow<Boolean>
}

internal class GlobalAppConfigStorageImpl(
    private val kaliumPreferences: KaliumPreferences
) : GlobalAppConfigStorage {

    private val isPersistentWebSocketConnectionEnabledFlow =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun enableLogging(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_LOGGING, enabled)
    }

    override fun isLoggingEnables(): Boolean = kaliumPreferences.getBoolean(ENABLE_LOGGING, true)

    override fun persistPersistentWebSocketConnectionStatus(enabled: Boolean) {
        kaliumPreferences.putBoolean(PERSISTENT_WEB_SOCKET_CONNECTION, enabled)
            .also { isPersistentWebSocketConnectionEnabledFlow.tryEmit(Unit) }
    }

    override fun isPersistentWebSocketConnectionEnabledFlow(): Flow<Boolean> =
        isPersistentWebSocketConnectionEnabledFlow
            .map {
                kaliumPreferences.getBoolean(PERSISTENT_WEB_SOCKET_CONNECTION, false)
            }.onStart {
                emit(kaliumPreferences.getBoolean(PERSISTENT_WEB_SOCKET_CONNECTION, false))
            }.distinctUntilChanged()


    private companion object {
        const val ENABLE_LOGGING = "enable_logging"
        const val PERSISTENT_WEB_SOCKET_CONNECTION = "persistent_web_socket_connection"
    }
}
