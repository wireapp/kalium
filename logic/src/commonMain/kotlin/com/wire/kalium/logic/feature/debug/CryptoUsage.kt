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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.util.DebugKaliumApi

/**
 * A cryptographic call site in kalium.
 *
 * Mirrors the enum of the same name in `core:cryptography`, which kalium depends on with `implementation`
 * and therefore cannot expose to callers.
 */
@DebugKaliumApi("Debug-only identifier of a cryptographic call site in kalium.")
public enum class CryptoUsage {
    /** `AESEncrypt.encryptFile` / `encryptData`, IV generation. */
    ASSET_ENCRYPTION_IV,

    /** `AESEncrypt.generateRandomAES256Key`. */
    ASSET_KEY,

    /** `AESEncrypt` and `AESDecrypt`, asset payloads. */
    ASSET_CIPHER,

    /** `SecurityHelper` database secrets and `RandomPassword`, via kalium's `SecureRandom` wrapper. */
    DATABASE_SECRET,
}
