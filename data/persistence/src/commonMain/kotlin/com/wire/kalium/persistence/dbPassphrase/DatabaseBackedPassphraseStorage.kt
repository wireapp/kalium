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

import com.wire.kalium.persistence.daokaliumdb.GlobalDbSecretEntity
import com.wire.kalium.persistence.daokaliumdb.DatabaseBackedGlobalSecretsCache
import com.wire.kalium.persistence.daokaliumdb.GlobalSecretsDAO
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock

internal class DatabaseBackedPassphraseStorage(
    private val globalSecretsCache: DatabaseBackedGlobalSecretsCache,
    private val clock: Clock = Clock.System
) : PassphraseStorage {
    constructor(
        globalSecretsDAO: GlobalSecretsDAO,
        clock: Clock = Clock.System
    ) : this(DatabaseBackedGlobalSecretsCache(globalSecretsDAO), clock)

    override fun getPassphrase(key: String): String? = runBlocking {
        globalSecretsCache.dbSecret(key)?.secret?.decodeToString()
    }

    override fun setPassphrase(key: String, passphrase: String) {
        require(passphrase.isNotEmpty()) {
            "Database passphrase must not be empty"
        }
        runBlocking {
            val currentSecret = globalSecretsCache.dbSecret(key)
            val now = clock.now().toEpochMilliseconds()
            globalSecretsCache.upsertDbSecret(
                GlobalDbSecretEntity(
                    alias = key,
                    secret = passphrase.encodeToByteArray(),
                    version = currentSecret?.version ?: DEFAULT_SECRET_VERSION,
                    createdAt = currentSecret?.createdAt ?: now,
                    updatedAt = now
                )
            )
        }
    }

    override fun clearPassphrase(key: String) {
        runBlocking {
            globalSecretsCache.deleteDbSecret(key)
        }
    }

    private companion object {
        const val DEFAULT_SECRET_VERSION = 1
    }
}
