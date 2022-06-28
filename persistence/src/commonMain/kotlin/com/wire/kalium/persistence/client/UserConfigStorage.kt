package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface UserConfigStorage {

    /**
     * save flag from the user settings to enable and disable the logging
     */
    fun enableLogging(enabled: Boolean)

    /**
     * get the saved flag to know if the logging enabled or not
     */
    fun isLoggingEnables(): Boolean

    /**
     * save flag from the file sharing api, and if the status changes
     */
    fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?)

    /**
     * get the saved flag that been saved to know if the file sharing is enabled or not with the flag
     * to know if there was a status change
     */
    fun isFileSharingEnabled(): IsFileSharingEnabledEntity?
}

@Serializable
data class IsFileSharingEnabledEntity(
    @SerialName("status") val status: Boolean,
    @SerialName("isStatusChanged") val isStatusChanged: Boolean?
)

class UserConfigStorageImpl(private val kaliumPreferences: KaliumPreferences) : UserConfigStorage {

    override fun enableLogging(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_LOGGING, enabled)
    }

    override fun isLoggingEnables(): Boolean =
        kaliumPreferences.getBoolean(ENABLE_LOGGING)

    override fun persistFileSharingStatus(status: Boolean, isStatusChanged: Boolean?) {
        kaliumPreferences.putSerializable(
            FILE_SHARING,
            IsFileSharingEnabledEntity(status, isStatusChanged),
            IsFileSharingEnabledEntity.serializer()
        )
    }

    override fun isFileSharingEnabled(): IsFileSharingEnabledEntity? =
        kaliumPreferences.getSerializable(FILE_SHARING, IsFileSharingEnabledEntity.serializer())


    private companion object {
        const val ENABLE_LOGGING = "enable_logging"
        const val FILE_SHARING = "file_sharing"
    }

}
