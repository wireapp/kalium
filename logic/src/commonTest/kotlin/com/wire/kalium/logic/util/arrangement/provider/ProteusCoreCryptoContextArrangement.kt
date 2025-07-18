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
package com.wire.kalium.logic.util.arrangement.provider

import com.wire.kalium.cryptography.*
import com.wire.kalium.cryptography.PreKeyCrypto
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock

internal interface ProteusCoreCryptoContextArrangement {
    val proteusContext: ProteusCoreCryptoContext

    suspend fun withNewPreKeysReturning(from: Int, count: Int, result: List<PreKeyCrypto>): ProteusCoreCryptoContextArrangement
    suspend fun withNewLastResortPreKeyReturning(result: PreKeyCrypto): ProteusCoreCryptoContextArrangement
    suspend fun withCreateSessionSuccess(): ProteusCoreCryptoContextArrangement
    suspend fun withCreateSessionThrowing(exception: Throwable): ProteusCoreCryptoContextArrangement
    suspend fun withDoesSessionExistReturning(result: Boolean): ProteusCoreCryptoContextArrangement
    suspend fun withGetLocalFingerprintReturning(fingerprint: ByteArray): ProteusCoreCryptoContextArrangement
    suspend fun withDeleteSessionSuccess(): ProteusCoreCryptoContextArrangement
}

internal class ProteusCoreCryptoContextArrangementImpl : ProteusCoreCryptoContextArrangement {

    override val proteusContext: ProteusCoreCryptoContext = mock(ProteusCoreCryptoContext::class)

    override suspend fun withNewPreKeysReturning(from: Int, count: Int, result: List<PreKeyCrypto>) = apply {
        coEvery { proteusContext.newPreKeys(from, count) }.returns(ArrayList(result))
    }

    override suspend fun withNewLastResortPreKeyReturning(result: PreKeyCrypto) = apply {
        coEvery { proteusContext.newLastResortPreKey() }.returns(result)
    }

    override suspend fun withCreateSessionSuccess() = apply {
        coEvery { proteusContext.createSession(any(), any()) }.returns(Unit)
    }

    override suspend fun withCreateSessionThrowing(exception: Throwable) = apply {
        coEvery { proteusContext.createSession(any(), any()) }.throws(exception)
    }

    override suspend fun withDoesSessionExistReturning(result: Boolean) = apply {
        coEvery { proteusContext.doesSessionExist(any()) }.returns(result)
    }

    override suspend fun withGetLocalFingerprintReturning(fingerprint: ByteArray) = apply {
        coEvery { proteusContext.getLocalFingerprint() }.returns(fingerprint)
    }

    override suspend fun withDeleteSessionSuccess() = apply {
        coEvery { proteusContext.deleteSession(any()) }.returns(Unit)
    }
}
