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
import com.wire.kalium.persistence.dbPassphrase.CryptoStoreKeyCreationGuard
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

actual class GlobalPrefProvider(
    rootPath: String,
    shouldEncryptData: Boolean = true,
    protectSettings: Boolean = false
) {
    private val settings = buildSettings(SettingOptions.AppSettings(shouldEncryptData), EncryptedSettingsPlatformParam(rootPath, protectSettings))
    private val kaliumPref = KaliumPreferencesSettings(settings)

    actual val authTokenStorage: AuthTokenStorage
        get() = AuthTokenStorageImpl(kaliumPref)
    actual val passphraseStorage: PassphraseStorage
        get() {
            val storage = PassphraseStorageImpl(kaliumPref)
            val protected = settings as? DesktopProtectedSettings ?: return storage
            return object : PassphraseStorage by storage, CryptoStoreKeyCreationGuard {
                override fun requireNewCryptoStore(rootDir: String) {
                    try {
                        Files.newDirectoryStream(Path.of(rootDir)).use {
                            check(!it.iterator().hasNext()) { "Missing key for existing Core Crypto store" }
                        }
                    } catch (_: NoSuchFileException) {
                        // Core Crypto creates the directory on its first open.
                    }
                }

                override fun setPassphraseIfAbsent(key: String, passphrase: String): String = protected.putIfAbsent(key, passphrase)
            }
        }
    actual val tokenStorage: TokenStorage
        get() = TokenStorageImpl(kaliumPref)
}
