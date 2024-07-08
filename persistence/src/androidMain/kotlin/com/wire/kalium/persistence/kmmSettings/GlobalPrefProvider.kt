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

import android.content.Context
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.AuthTokenStorageImpl
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl

actual class GlobalPrefProvider(context: Context, shouldEncryptData: Boolean = true) {

    private val encryptedSettingsHolder: KaliumPreferences = KaliumPreferencesSettings(
        buildSettings(SettingOptions.AppSettings(shouldEncryptData), EncryptedSettingsPlatformParam(context))
    )

    actual val authTokenStorage: AuthTokenStorage
        get() = AuthTokenStorageImpl(encryptedSettingsHolder)
    actual val passphraseStorage: PassphraseStorage
        get() = PassphraseStorageImpl(encryptedSettingsHolder)
    actual val tokenStorage: TokenStorage
        get() = TokenStorageImpl(encryptedSettingsHolder)
}
