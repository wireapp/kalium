/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.protection

import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.Assume.assumeTrue

class MacStorageKeyStoreTest {
    @Test
    fun storesAndLoadsIndependentKeysInAnExplicitTestKeychain() {
        val keychain = System.getenv("KALIUM_TEST_KEYCHAIN")
        assumeTrue(System.getProperty("os.name") == "Mac OS X" && !keychain.isNullOrBlank())
        val path = Path.of(checkNotNull(keychain))
        val keyId = UUID.randomUUID().toString()
        val store = MacStorageKeyStore(path)
        assertFailsWith<IllegalStateException> { store.load(keyId, allowCreation = false) }
        val first = store.load(keyId, allowCreation = true)
        val reopened = MacStorageKeyStore(path).load(keyId, allowCreation = false)
        val second = store.load(UUID.randomUUID().toString(), allowCreation = true)
        try {
            assertEquals(32, first.size)
            assertContentEquals(first, reopened)
            assertFalse(first.contentEquals(second))
        } finally {
            first.fill(0)
            reopened.fill(0)
            second.fill(0)
        }
    }
}
