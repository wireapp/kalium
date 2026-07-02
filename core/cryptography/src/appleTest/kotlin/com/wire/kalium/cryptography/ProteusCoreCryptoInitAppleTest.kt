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

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ProteusCoreCryptoInitAppleTest {

    @Test
    fun givenFreshProteusStore_whenOpeningCoreCrypto_thenCreatesPreKey() = runTest {
        val rootDir = NSTemporaryDirectory() +
            "wiretui-proteus-repro-" +
            NSUUID.UUID().UUIDString +
            "/proteus"

        val client = coreCryptoCentral(
            rootDir = rootDir,
            passphrase = ByteArray(32) { index -> index.toByte() },
        ).proteusClient()

        val preKey = client.newLastResortPreKey()

        assertEquals(65535, preKey.id)
        client.close()
    }
}
