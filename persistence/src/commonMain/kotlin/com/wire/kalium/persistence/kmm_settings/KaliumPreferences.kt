package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import com.wire.kalium.persistence.Util.JsonSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class KaliumPreferences(
    val encryptedSettings: Settings
) {

    fun remove(key: String) = encryptedSettings.remove(key)

    fun exitsValue(key: String) = encryptedSettings.keys.contains(key)

    fun putString(key: String, value: String) {
        encryptedSettings[key] = value
    }
    fun putString(key: String, value: () -> String) = putString(key, value())
    fun getString(key: String): String? = encryptedSettings[key]

    inline fun <reified T> putSerializable(key: String, value: T) {
        // TODO: try catch for Serialization exceptions
        encryptedSettings[key] = JsonSerializer().encodeToString(value)
    }
    inline fun <reified T> putSerializable(key: String, value: () -> T) = putSerializable(key, value())
    inline fun <reified T> getSerializable(key: String): T? {
        val jsonString: String? = encryptedSettings[key]
        return jsonString?.let {
            JsonSerializer().decodeFromString(it)
        } ?: run { null }
    }


    private companion object {
        const val bytesToStringSeparator = "|"
    }
}
