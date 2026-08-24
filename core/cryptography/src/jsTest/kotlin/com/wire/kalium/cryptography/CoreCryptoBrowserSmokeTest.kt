/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.cryptography

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class CoreCryptoBrowserSmokeTest {
    @Test
    fun opensDatabaseAndRunsProteusThroughTheV10BrowserPackage() = runTest {
        val databaseName = "kalium-core-crypto-js-${Random.nextLong()}"
        val central = coreCryptoCentral(databaseName, ByteArray(DATABASE_KEY_SIZE) { it.toByte() })
        val proteus = central.proteusClient()

        try {
            val fingerprint = proteus.transaction("browser-smoke-test") { it.getLocalFingerprint() }
            assertTrue(fingerprint.isNotBlank())
        } finally {
            proteus.close()
        }
    }

    private companion object {
        const val DATABASE_KEY_SIZE = 32
    }
}
