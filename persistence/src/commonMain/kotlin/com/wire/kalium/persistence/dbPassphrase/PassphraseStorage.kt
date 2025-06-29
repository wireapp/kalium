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

package com.wire.kalium.persistence.dbPassphrase

import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import io.mockative.Mockable

@Mockable
interface PassphraseStorage {
    fun getPassphrase(key: String): String?
    fun setPassphrase(key: String, passphrase: String)
    fun clearPassphrase(key: String)
}

internal class PassphraseStorageImpl internal constructor(
    private val kaliumPreferences: KaliumPreferences
) : PassphraseStorage {
    override fun getPassphrase(key: String): String? = kaliumPreferences.getString(key)

    override fun setPassphrase(key: String, passphrase: String) {
        kaliumPreferences.putString(key, passphrase)
    }

    override fun clearPassphrase(key: String) {
        kaliumPreferences.remove(key)
    }
}
