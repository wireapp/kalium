/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.time.Duration.Companion.seconds

/**
 * Performs a work using a CC Transaction, returning its result, exactly like the regular withLock function.
 * However, this will monitor the work and log warnings using the provided [workIdentifier]
 * every 10 seconds, while the work isn't completed.
 */
@Suppress("MagicNumber")
internal suspend fun <T> CoreCrypto.transaction(
    workIdentifier: String,
    block: suspend (context: CoreCryptoContext) -> T
): T = coroutineScope {
    val workUniqueId = "$workIdentifier;${Random.nextUInt()}"
    val asyncLockWork = async {
        val result = transaction {
            kaliumLogger.d("CC Transaction '$workUniqueId' started.")
            block(it)
        }
        kaliumLogger.d("CC Transaction '$workUniqueId' completed.")
        result
    }
    val startInstant = Clock.System.now()
    val waitJob = launch {
        while (asyncLockWork.isActive) {
            delay(10.seconds)
            if (asyncLockWork.isActive) {
                val currentInstant = Clock.System.now()
                val elapsedTime = currentInstant.minus(startInstant)
                kaliumLogger.w(
                    "Waiting for CC Transaction '$workUniqueId' to complete for a long time! Elapsed time: $elapsedTime."
                )
            }
        }
    }
    asyncLockWork.invokeOnCompletion {
        waitJob.cancel()
    }
    asyncLockWork.await()
}
