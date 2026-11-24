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

import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.AuthTokenStorageImpl
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder

actual class GlobalPrefProvider private constructor(
    actual val authTokenStorage: AuthTokenStorage,
    actual val tokenStorage: TokenStorage,
    actual val passphraseStorage: PassphraseStorage,
) {
    constructor(rootPath: String, shouldEncryptData: Boolean = true) : this(
        KaliumPreferencesSettings(
            buildSettings(
                SettingOptions.AppSettings(shouldEncryptData),
                EncryptedSettingsPlatformParam(rootPath)
            )
        )
    )

    private constructor(preferences: KaliumPreferences) : this(
        AuthTokenStorageImpl(preferences), TokenStorageImpl(preferences), PassphraseStorageImpl(preferences)
    )

    companion object {
        /** Stores global settings in the authenticated, encrypted JVM global database. */
        fun protectedStorage(database: GlobalDatabaseBuilder): GlobalPrefProvider {
            check(database.isProtectedJvm) { "Global settings require a protected JVM database" }
            val preferences = ProtectedJvmPreferences(database)
            return GlobalPrefProvider(
                ProtectedAuthTokenStorage(preferences), ProtectedTokenStorage(preferences), ProtectedPassphraseStorage(preferences)
            )
        }
    }
}
