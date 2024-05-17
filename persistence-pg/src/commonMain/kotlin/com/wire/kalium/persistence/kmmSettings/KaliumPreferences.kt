/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.kmmSettings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import com.wire.kalium.persistence.util.JsonSerializer
import kotlinx.serialization.KSerializer

@Suppress("TooManyFunctions")
interface KaliumPreferences {
    fun remove(key: String)
    fun hasValue(key: String): Boolean
    fun putString(key: String, value: String?)
    fun putString(key: String, value: () -> String?) = putString(key, value())
    fun getString(key: String): String?

    fun <T> putSerializable(key: String, value: T, kSerializer: KSerializer<T>)
    fun <T> putSerializable(key: String, value: () -> T, kSerializer: KSerializer<T>) =
        putSerializable(key, value(), kSerializer)

    fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T?

    fun nuke()

    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getBoolean(key: String): Boolean?

    fun putLong(key: String, value: Long)
    fun getLong(key: String, defaultValue: Long): Long
    fun getLong(key: String): Long?

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

    override fun putLong(key: String, value: Long) {
        encryptedSettings.putLong(key, value)
    }

    override fun getLong(key: String, defaultValue: Long): Long = encryptedSettings.getLong(key, defaultValue)

    override fun getLong(key: String): Long? = encryptedSettings.getLongOrNull(key)

    override fun remove(key: String) = encryptedSettings.remove(key)

    override fun hasValue(key: String) = encryptedSettings.keys.contains(key)

    override fun putString(key: String, value: String?) {
        encryptedSettings[key] = value
    }

    override fun getString(key: String): String? = encryptedSettings[key]

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
