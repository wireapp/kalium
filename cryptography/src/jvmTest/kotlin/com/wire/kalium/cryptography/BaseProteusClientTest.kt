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

package com.wire.kalium.cryptography

import kotlinx.coroutines.test.TestCoroutineScheduler
import java.nio.file.Files

actual open class BaseProteusClientTest {

    private val testCoroutineScheduler = TestCoroutineScheduler()

    actual fun createProteusStoreRef(userId: CryptoUserID): ProteusStoreRef {
        val root = Files.createTempDirectory("proteus").toFile()
        val keyStore = root.resolve("keystore-${userId.value}")
        return ProteusStoreRef(keyStore.absolutePath)
    }

    actual suspend fun createProteusClient(
        proteusStore: ProteusStoreRef,
        databaseKey: ProteusDBSecret?
    ): ProteusClient {
        return databaseKey?.let {
            coreCryptoCentral(proteusStore.value, it.value).proteusClient()
        } ?: cryptoboxProteusClient(proteusStore.value, testCoroutineScheduler, testCoroutineScheduler)
    }
}
