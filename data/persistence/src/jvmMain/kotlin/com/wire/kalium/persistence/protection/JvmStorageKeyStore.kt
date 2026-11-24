/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.protection

import com.sun.jna.Platform

/** Provides independent 256-bit storage keys. Callers own and should clear the returned array. */
fun interface JvmStorageKeyStore {
    /**
     * Loads a key or, if there is no item and [allowCreation] is true, stores a new random key.
     * Missing keys, denial and other storage errors throw. An existing key is never replaced.
     */
    fun load(keyId: String, allowCreation: Boolean): ByteArray

    companion object {
        /**
         * Uses the macOS default Keychain. Other operating systems have no built-in provider.
         * A locked or unavailable Keychain can require user interaction or report an error.
         */
        fun system(): JvmStorageKeyStore {
            if (!Platform.isMac()) {
                throw UnsupportedOperationException("System storage keys are currently supported only on macOS")
            }
            return MacStorageKeyStore()
        }
    }
}
