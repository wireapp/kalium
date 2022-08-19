package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface UserConfigStorage {

    /**
     * save flag from the user settings to enable and disable MLS
     */
    fun enableMLS(enabled: Boolean)

    /**
     * get the saved flag to know if MLS enabled or not
     */
    fun isMLSEnabled(): Boolean

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

    /**
     * save flag from the file sharing api, and if the status changes
     */
    fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?)

    /**
     * get the saved flag that been saved to know if the file sharing is enabled or not with the flag
     * to know if there was a status change
     */
    fun isFileSharingEnabled(): IsFileSharingEnabledEntity?

    /**
     * returns the Flow of file sharing status
     */
    fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?>

}

@Serializable
data class IsFileSharingEnabledEntity(
    @SerialName("status") val status: Boolean,
    @SerialName("isStatusChanged") val isStatusChanged: Boolean?
)

class UserConfigStorageImpl(private val kaliumPreferences: KaliumPreferences) : UserConfigStorage {

    private val isFileSharingEnabledFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val isPersistentWebSocketConnectionEnabledFlow =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun enableMLS(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_MLS, enabled)
    }

    override fun isMLSEnabled(): Boolean =
        kaliumPreferences.getBoolean(ENABLE_MLS, false)

    override fun enableLogging(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_LOGGING, enabled)
    }

    override fun isLoggingEnables(): Boolean =
        kaliumPreferences.getBoolean(ENABLE_LOGGING, true)

    override fun persistPersistentWebSocketConnectionStatus(enabled: Boolean) {
        kaliumPreferences.putBoolean(PERSISTENT_WEB_SOCKET_CONNECTION, enabled)
            .also { isPersistentWebSocketConnectionEnabledFlow.tryEmit(Unit) }
    }

    override fun isPersistentWebSocketConnectionEnabledFlow(): Flow<Boolean> = isPersistentWebSocketConnectionEnabledFlow
        .map { kaliumPreferences.getBoolean(PERSISTENT_WEB_SOCKET_CONNECTION, false) }
        .onStart { emit(kaliumPreferences.getBoolean(PERSISTENT_WEB_SOCKET_CONNECTION, false)) }
        .distinctUntilChanged()

    override fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?) {
        kaliumPreferences.putSerializable(
            FILE_SHARING,
            IsFileSharingEnabledEntity(status, isStatusChanged),
            IsFileSharingEnabledEntity.serializer().also {
                isFileSharingEnabledFlow.tryEmit(Unit)
            }
        )
    }

    override fun isFileSharingEnabled(): IsFileSharingEnabledEntity? =
        kaliumPreferences.getSerializable(FILE_SHARING, IsFileSharingEnabledEntity.serializer())

    override fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?> = isFileSharingEnabledFlow
        .map { isFileSharingEnabled() }
        .onStart { emit(isFileSharingEnabled()) }
        .distinctUntilChanged()

    private companion object {
        const val ENABLE_LOGGING = "enable_logging"
        const val FILE_SHARING = "file_sharing"
        const val ENABLE_MLS = "enable_mls"
        const val PERSISTENT_WEB_SOCKET_CONNECTION = "persistent_web_socket_connectiona"
    }
}
