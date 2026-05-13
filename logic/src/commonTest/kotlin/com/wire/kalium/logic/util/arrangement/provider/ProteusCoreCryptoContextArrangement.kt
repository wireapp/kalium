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

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any as mokkeryAny
import dev.mokkery.mock as mokkeryMock

internal interface ProteusCoreCryptoContextArrangement {
    val proteusContext: ProteusCoreCryptoContext

    suspend fun withNewLastResortPreKeyReturning(result: PreKeyCrypto): ProteusCoreCryptoContextArrangement
    suspend fun withCreateSessionSuccess(): ProteusCoreCryptoContextArrangement
    suspend fun withCreateSessionThrowing(exception: Throwable): ProteusCoreCryptoContextArrangement
    suspend fun withDoesSessionExistReturning(result: Boolean): ProteusCoreCryptoContextArrangement
    suspend fun withGetLocalFingerprintReturning(fingerprint: String): ProteusCoreCryptoContextArrangement
    suspend fun withDeleteSessionSuccess(): ProteusCoreCryptoContextArrangement
}

internal open class ProteusCoreCryptoContextArrangementMokkeryImpl : ProteusCoreCryptoContextArrangement {

    override val proteusContext: ProteusCoreCryptoContext = mokkeryMock()

    override suspend fun withNewLastResortPreKeyReturning(result: PreKeyCrypto) = apply {
        everySuspend { proteusContext.newLastResortPreKey() } returns result
    }

    override suspend fun withCreateSessionSuccess() = apply {
        everySuspend { proteusContext.createSession(mokkeryAny(), mokkeryAny()) } returns Unit
    }

    override suspend fun withCreateSessionThrowing(exception: Throwable) = apply {
        everySuspend { proteusContext.createSession(mokkeryAny(), mokkeryAny()) } throws exception
    }

    override suspend fun withDoesSessionExistReturning(result: Boolean) = apply {
        everySuspend { proteusContext.doesSessionExist(mokkeryAny()) } returns result
    }

    override suspend fun withGetLocalFingerprintReturning(fingerprint: String) = apply {
        everySuspend { proteusContext.getLocalFingerprint() } returns fingerprint
    }

    override suspend fun withDeleteSessionSuccess() = apply {
        everySuspend { proteusContext.deleteSession(mokkeryAny()) } returns Unit
    }
}

internal class ProteusCoreCryptoContextArrangementImpl : ProteusCoreCryptoContextArrangementMokkeryImpl()
