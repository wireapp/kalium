package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("TooManyFunctions")
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

    @Deprecated("must be moved to the user specific storage")
            /**
             * save flag from the file sharing api, and if the status changes
             */
    fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?)

    @Deprecated("must be moved to the user specific storage")
            /**
             * get the saved flag that been saved to know if the file sharing is enabled or not with the flag
             * to know if there was a status change
             */
    fun isFileSharingEnabled(): IsFileSharingEnabledEntity?

    @Deprecated("must be moved to the user specific storage")
            /**
             * returns the Flow of file sharing status
             */
    fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?>

    @Deprecated("must be moved to the user specific storage")
            /**
             * returns a Flow containing the status and list of classified domains
             */
    fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity>

    @Deprecated("must be moved to the user specific storage")
            /**
             * save the flag and list of trusted domains
             */
    fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>)
}

@Serializable
data class IsFileSharingEnabledEntity(
    @SerialName("status") val status: Boolean,
    @SerialName("isStatusChanged") val isStatusChanged: Boolean?
)

@Serializable
data class ClassifiedDomainsEntity(
    @SerialName("status") val status: Boolean,
    @SerialName("trustedDomains") val trustedDomains: List<String>,
)

@Suppress("TooManyFunctions")
internal class UserConfigStorageImpl internal constructor(
    private val kaliumPreferences: KaliumPreferences
) : UserConfigStorage {

    private val isFileSharingEnabledFlow =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val isPersistentWebSocketConnectionEnabledFlow =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val isClassifiedDomainsEnabledFlow =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun enableMLS(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_MLS, enabled)
    }

    override fun isMLSEnabled(): Boolean = kaliumPreferences.getBoolean(ENABLE_MLS, false)

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

    override fun persistFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ) {
        kaliumPreferences.putSerializable(
            FILE_SHARING,
            IsFileSharingEnabledEntity(status, isStatusChanged),
            IsFileSharingEnabledEntity.serializer().also {
                isFileSharingEnabledFlow.tryEmit(Unit)
            }
        )
    }

    @Deprecated("must be moved to the user specific storage")
    override fun isFileSharingEnabled(): IsFileSharingEnabledEntity? =
        kaliumPreferences.getSerializable(FILE_SHARING, IsFileSharingEnabledEntity.serializer())

    @Deprecated("must be moved to the user specific storage")
    override fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?> = isFileSharingEnabledFlow
        .map { isFileSharingEnabled() }
        .onStart { emit(isFileSharingEnabled()) }
        .distinctUntilChanged()

    @Deprecated("must be moved to the user specific storage")
    override fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity> {
        return isClassifiedDomainsEnabledFlow
            .map {
                kaliumPreferences.getSerializable(ENABLE_CLASSIFIED_DOMAINS, ClassifiedDomainsEntity.serializer())!!
            }.onStart {
                emit(
                    kaliumPreferences.getSerializable(
                        ENABLE_CLASSIFIED_DOMAINS,
                        ClassifiedDomainsEntity.serializer()
                    )!!
                )
            }.distinctUntilChanged()
    }

    @Deprecated("must be moved to the user specific storage")
    override fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>) {
        kaliumPreferences.putSerializable(
            ENABLE_CLASSIFIED_DOMAINS,
            ClassifiedDomainsEntity(status, classifiedDomains),
            ClassifiedDomainsEntity.serializer()
        ).also {
            isClassifiedDomainsEnabledFlow.tryEmit(Unit)
        }
    }

    private companion object {
        const val ENABLE_LOGGING = "enable_logging"
        const val FILE_SHARING = "file_sharing"
        const val ENABLE_MLS = "enable_mls"
        const val ENABLE_CLASSIFIED_DOMAINS = "enable_classified_domains"
        const val PERSISTENT_WEB_SOCKET_CONNECTION = "persistent_web_socket_connection"
    }
}
