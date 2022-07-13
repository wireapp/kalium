package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import com.wire.kalium.persistence.util.JsonSerializer
import kotlinx.serialization.KSerializer

interface KaliumPreferences {
    fun remove(key: String)
    fun hasValue(key: String): Boolean
    fun putString(key: String, value: String?)
    fun putString(key: String, value: () -> String?) = putString(key, value())
    fun getString(key: String): String?

    fun <T> putSerializable(key: String, value: T, kSerializer: KSerializer<T>)
    fun <T> putSerializable(key: String, value: () -> T, kSerializer: KSerializer<T>) = putSerializable(key, value(), kSerializer)
    fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T?

    fun nuke()

    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun getBoolean(key: String): Boolean?

}

class KaliumPreferencesSettings(
    private val encryptedSettings: Settings
) : KaliumPreferences {

    override fun nuke() = encryptedSettings.clear()
    override fun putBoolean(key: String, value: Boolean) {
        encryptedSettings.putBoolean(key, value)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        encryptedSettings.getBoolean(key, defaultValue)

    override fun getBoolean(key: String): Boolean? =
        encryptedSettings.getBooleanOrNull(key)

    override fun remove(key: String) = encryptedSettings.remove(key)

    override fun hasValue(key: String) = encryptedSettings.keys.contains(key)

    override fun putString(key: String, value: String?) {
        encryptedSettings[key] = value
    }

    override fun getString(key: String): String? = encryptedSettings[key]

    //TODO(IMPORTANT): Make sure we use @SerialName before release
    override fun <T> putSerializable(key: String, value: T, kSerializer: KSerializer<T>) {
        // TODO(refactor): try catch for Serialization exceptions
        encryptedSettings[key] = JsonSerializer().encodeToString(kSerializer, value)
    }

    override fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T? {
        val jsonString: String? = encryptedSettings[key]
        return jsonString?.let {
            JsonSerializer().decodeFromString(kSerializer, it)
        } ?: run { null }
    }
}
