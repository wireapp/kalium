/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

/**
 * The encrypted settings can't be used: a settings file is plaintext or was changed, or its master
 * key is missing, belongs to another key store, or the system key store is unavailable.
 *
 * Kalium never falls back to empty settings here, since that would lose the keys of the
 * CoreCrypto keystores. Catching this is the place to offer starting over locally.
 */
class SettingsEncryptionException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
