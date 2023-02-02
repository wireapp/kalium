/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
actual open class BaseProteusClientTest {

    private val standardScope = StandardTestDispatcher()

    actual fun createProteusStoreRef(userId: CryptoUserID): ProteusStoreRef {
        val root = Files.createTempDirectory("proteus").toFile()
        val keyStore = root.resolve("keystore-${userId.value}")
        return ProteusStoreRef(keyStore.absolutePath)
    }

    actual fun createProteusClient(proteusStore: ProteusStoreRef, databaseKey: ProteusDBSecret?): ProteusClient {
        return ProteusClientImpl(proteusStore.value, databaseKey, standardScope, standardScope)
    }
}
