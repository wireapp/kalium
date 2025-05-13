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

import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

actual open class BaseProteusClientTest actual constructor() {

    actual fun createProteusStoreRef(userId: CryptoUserID): ProteusStoreRef {
        val rootDir = NSURL.fileURLWithPath(NSTemporaryDirectory() + "proteus/${userId.value}", isDirectory = true)
        return ProteusStoreRef(rootDir.path!!)
    }

    actual suspend fun createProteusClient(
        proteusStore: ProteusStoreRef,
        databaseKey: ProteusDBSecret?
    ): ProteusClient {
        return coreCryptoCentral(proteusStore.value, "secret", ByteArray(32) { 0 }, true).proteusClient()
    }

}
