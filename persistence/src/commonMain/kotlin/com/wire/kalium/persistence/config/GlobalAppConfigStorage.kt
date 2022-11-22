package com.wire.kalium.persistence.config

import com.wire.kalium.persistence.kmmSettings.KaliumPreferences

interface GlobalAppConfigStorage {

    /**
     * save flag from the user settings to enable and disable the logging
     */
    fun enableLogging(enabled: Boolean)

    /**
     * get the saved flag to know if the logging enabled or not
     */
    fun isLoggingEnables(): Boolean
}

internal class GlobalAppConfigStorageImpl(
    private val kaliumPreferences: KaliumPreferences
) : GlobalAppConfigStorage {

    override fun enableLogging(enabled: Boolean) {
        kaliumPreferences.putBoolean(ENABLE_LOGGING, enabled)
    }

    override fun isLoggingEnables(): Boolean = kaliumPreferences.getBoolean(ENABLE_LOGGING, true)

    private companion object {
        const val ENABLE_LOGGING = "enable_logging"
    }
}
