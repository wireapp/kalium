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
package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.DecryptedBatch
import com.wire.kalium.cryptography.exceptions.CryptographyMLSException
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

inline fun <reified T : CryptographyMLSException> assertMLSException(block: () -> Unit) {
    val result = runCatching(block)

    assertTrue(result.isFailure, "Expected exception of type ${T::class.simpleName}, but no exception was thrown.")

    val exception = result.exceptionOrNull()!!
    println("Exception: ${exception.cause}")
    val mapped = mapMLSException(exception)

    assertTrue(
        mapped is T,
        "Expected exception of type ${T::class.simpleName}, but was ${mapped::class.simpleName}."
    )
}

inline fun <reified T : CryptographyMLSException> assertMLSBatchFailure(result: DecryptedBatch) {
    val error = result.failedMessage?.error
    assertNotNull(error, "Expected failedMessage with error, but it was null.")
    val mapped = mapMLSException(error)
    assertTrue(mapped is T, "Expected ${T::class.simpleName}, but got ${mapped::class.simpleName}")
}

