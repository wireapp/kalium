package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import com.wire.kalium.persistence.Util.JsonSerializer
import kotlinx.serialization.KSerializer

interface KaliumPreferences {
    fun remove(key: String)
    fun hasValue(key: String): Boolean
    fun putString(key: String, value: String)
    fun putString(key: String, value: () -> String) = putString(key, value())
    fun getString(key: String): String?

    fun <T> putSerializable(key: String, value: T, kSerializer: KSerializer<T>)
    fun <T> putSerializable(key: String, value: () -> T, kSerializer: KSerializer<T>) = putSerializable(key, value(), kSerializer)
    fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T?
}

class KaliumPreferencesSettings(
    private val encryptedSettings: Settings
) : KaliumPreferences {

    override fun remove(key: String) = encryptedSettings.remove(key)

    override fun hasValue(key: String) = encryptedSettings.keys.contains(key)

    override fun putString(key: String, value: String) {
        encryptedSettings[key] = value
    }

    override fun getString(key: String): String? = encryptedSettings[key]

    override fun <T> putSerializable(key: String, value: T, kSerializer: KSerializer<T>) {
        // TODO: try catch for Serialization exceptions
        encryptedSettings[key] = JsonSerializer().encodeToString(kSerializer, value)
    }

    override fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T? {
        val jsonString: String? = encryptedSettings[key]
        return jsonString?.let {
            JsonSerializer().decodeFromString(kSerializer, it)
        } ?: run { null }
    }
}
