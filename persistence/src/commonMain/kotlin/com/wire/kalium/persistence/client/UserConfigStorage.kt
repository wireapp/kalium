package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
interface UserConfigStorage {

    /**
     * save flag from the user settings to enable and disable MLS
     */
    suspend fun enableMLS(enabled: Boolean)

    /**
     * get the saved flag to know if MLS enabled or not
     */
    suspend fun isMLSEnabled(): Boolean

    /**
     * save flag from the user settings to enable and disable the logging
     */
    suspend fun enableLogging(enabled: Boolean)

    /**
     * get the saved flag to know if the logging enabled or not
     */
    suspend fun isLoggingEnables(): Boolean

    /**
     * save flag from the user settings to enable and disable the persistent webSocket connection
     */
    suspend fun persistPersistentWebSocketConnectionStatus(enabled: Boolean)

    /**
     * get the saved flag to know if the persistent webSocket connection enabled or not
     */
    fun isPersistentWebSocketConnectionEnabledFlow(): Flow<Boolean>

    /**
     * save flag from the file sharing api, and if the status changes
     */
    suspend fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?)

    /**
     * get the saved flag that been saved to know if the file sharing is enabled or not with the flag
     * to know if there was a status change
     */
    suspend fun isFileSharingEnabled(): IsFileSharingEnabledEntity?

    /**
     * returns the Flow of file sharing status
     */
    fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?>

    /**
     * returns a Flow containing the status and list of classified domains
     */
    fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity>

    /**
     * save the flag and list of trusted domains
     */
    suspend fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>)
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
    private val kaliumPreferences: KaliumPreferences,
    private val coroutineContext: CoroutineContext
) : UserConfigStorage {

    private val isFileSharingEnabledFlow =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val isPersistentWebSocketConnectionEnabledFlow =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val isClassifiedDomainsEnabledFlow =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override suspend fun enableMLS(enabled: Boolean) = withContext(coroutineContext) {
        kaliumPreferences.putBoolean(ENABLE_MLS, enabled)
    }

    override suspend fun isMLSEnabled(): Boolean = withContext(coroutineContext) {
        kaliumPreferences.getBoolean(ENABLE_MLS, false)
    }

    override suspend fun enableLogging(enabled: Boolean) = withContext(coroutineContext) {
        kaliumPreferences.putBoolean(ENABLE_LOGGING, enabled)
    }

    override suspend fun isLoggingEnables(): Boolean = withContext(coroutineContext) {
        kaliumPreferences.getBoolean(ENABLE_LOGGING, true)
    }

    override suspend fun persistPersistentWebSocketConnectionStatus(enabled: Boolean) {
        withContext(coroutineContext) { kaliumPreferences.putBoolean(PERSISTENT_WEB_SOCKET_CONNECTION, enabled) }
            .also { isPersistentWebSocketConnectionEnabledFlow.tryEmit(Unit) }
    }

    override fun isPersistentWebSocketConnectionEnabledFlow(): Flow<Boolean> =
        isPersistentWebSocketConnectionEnabledFlow
            .map {
                withContext(coroutineContext) { kaliumPreferences.getBoolean(PERSISTENT_WEB_SOCKET_CONNECTION, false) }
            }.onStart {
                withContext(coroutineContext) {
                    emit(kaliumPreferences.getBoolean(PERSISTENT_WEB_SOCKET_CONNECTION, false))
                }
            }.distinctUntilChanged()

    override suspend fun persistFileSharingStatus(
        status: Boolean,
        isStatusChanged: Boolean?
    ) = withContext(coroutineContext) {
        kaliumPreferences.putSerializable(
            FILE_SHARING,
            IsFileSharingEnabledEntity(status, isStatusChanged),
            IsFileSharingEnabledEntity.serializer().also {
                isFileSharingEnabledFlow.tryEmit(Unit)
            }
        )
    }

    override suspend fun isFileSharingEnabled(): IsFileSharingEnabledEntity? = withContext(coroutineContext) {
        kaliumPreferences.getSerializable(FILE_SHARING, IsFileSharingEnabledEntity.serializer())
    }

    override fun isFileSharingEnabledFlow(): Flow<IsFileSharingEnabledEntity?> = isFileSharingEnabledFlow
        .map { isFileSharingEnabled() }
        .onStart { emit(isFileSharingEnabled()) }
        .distinctUntilChanged()

    override fun isClassifiedDomainsEnabledFlow(): Flow<ClassifiedDomainsEntity> {
        return isClassifiedDomainsEnabledFlow
            .map {
                withContext(coroutineContext) {
                    kaliumPreferences.getSerializable(ENABLE_CLASSIFIED_DOMAINS, ClassifiedDomainsEntity.serializer())!!
                }
            }.onStart {
                withContext(coroutineContext) {
                    emit(
                        kaliumPreferences.getSerializable(
                            ENABLE_CLASSIFIED_DOMAINS,
                            ClassifiedDomainsEntity.serializer()
                        )!!
                    )
                }
            }.distinctUntilChanged()
    }

    override suspend fun persistClassifiedDomainsStatus(status: Boolean, classifiedDomains: List<String>) {
        withContext(coroutineContext) {
            kaliumPreferences.putSerializable(
                ENABLE_CLASSIFIED_DOMAINS,
                ClassifiedDomainsEntity(status, classifiedDomains),
                ClassifiedDomainsEntity.serializer()
            )
        }.also {
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
