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
package com.wire.kalium.cryptography

import com.ionspin.kotlin.crypto.LibsodiumInitializer

public object LibsodiumInitializer {

    public suspend fun initializeLibsodiumIfNeeded() {
        if (!LibsodiumInitializer.isInitialized()) {
            try {
                LibsodiumInitializer.initialize()
            } catch (ise: IllegalStateException) {
                // Needed because of https://github.com/ionspin/kotlin-multiplatform-libsodium/issues/67
                // Code 1 is fine. It means Libsodium was already initialised.
                // This can happen when multiple threads are initializing libsodium,
                // or when another library (like corecrypto), has initialised it.
                if (ise.message != "Libsodium returned an unexpected return code 1") {
                    throw ise
                }
            }
        }
    }
}
