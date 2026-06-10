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
import com.wire.kalium.persistence.client.DatabaseBackedAuthTokenStorage
import com.wire.kalium.persistence.client.DatabaseBackedTokenStorage
import com.wire.kalium.persistence.client.TokenStorage
import com.wire.kalium.persistence.client.TokenStorageImpl
import com.wire.kalium.persistence.daokaliumdb.DatabaseBackedGlobalSecretsCache
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.persistence.dbPassphrase.DatabaseBackedPassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorageImpl

actual class GlobalPrefProvider {

    private val encryptedSettingsHolder: KaliumPreferences?
    private val globalDatabaseBuilder: GlobalDatabaseBuilder?
    private val globalSecretsCache: DatabaseBackedGlobalSecretsCache? by lazy {
        globalDatabaseBuilder?.let { DatabaseBackedGlobalSecretsCache(it.globalSecretsDAO) }
    }

    constructor(context: Context, shouldEncryptData: Boolean = true) {
        encryptedSettingsHolder = KaliumPreferencesSettings(
            buildSettings(SettingOptions.AppSettings(shouldEncryptData), EncryptedSettingsPlatformParam(context))
        )
        globalDatabaseBuilder = null
    }

    constructor(globalDatabaseBuilder: GlobalDatabaseBuilder) {
        encryptedSettingsHolder = null
        this.globalDatabaseBuilder = globalDatabaseBuilder
    }

    actual val authTokenStorage: AuthTokenStorage by lazy {
        globalSecretsCache?.let { DatabaseBackedAuthTokenStorage(it) }
            ?: AuthTokenStorageImpl(requireNotNull(encryptedSettingsHolder))
    }
    actual val passphraseStorage: PassphraseStorage by lazy {
        globalSecretsCache?.let { DatabaseBackedPassphraseStorage(it) }
            ?: PassphraseStorageImpl(requireNotNull(encryptedSettingsHolder))
    }
    actual val tokenStorage: TokenStorage by lazy {
        globalSecretsCache?.let { DatabaseBackedTokenStorage(it) }
            ?: TokenStorageImpl(requireNotNull(encryptedSettingsHolder))
    }
}
