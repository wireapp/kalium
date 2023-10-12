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
package com.wire.kalium.logic.util.arrangement

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface PreKeyRepositoryArrangement {
    val preKeyRepository: PreKeyRepository

    fun withRemotelyAvailablePreKeysReturning(result: Either<CoreFailure, List<Int>>)

    fun withUploadNewPrekeyBatchReturning(result: Either<CoreFailure, Unit>)

    fun withGenerateNewPreKeysReturning(result: Either<CoreFailure, List<PreKeyCrypto>>)

    fun withMostRecentPreKeyId(result: Either<StorageFailure, Int>)

    fun withUpdatingMostRecentPrekeyReturning(result: Either<StorageFailure, Unit>)

    fun withSetLastPreKeyUploadInstantReturning(result: Either<StorageFailure, Unit>)

    fun withObserveLastPreKeyUploadInstantReturning(flow: Flow<Instant?>)
}

internal class PreKeyRepositoryArrangementImpl : PreKeyRepositoryArrangement {
    @Mock
    override val preKeyRepository: PreKeyRepository = mock(PreKeyRepository::class)

    override fun withRemotelyAvailablePreKeysReturning(result: Either<CoreFailure, List<Int>>) {
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::fetchRemotelyAvailablePrekeys)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withUploadNewPrekeyBatchReturning(result: Either<CoreFailure, Unit>) {
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::uploadNewPrekeyBatch)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withGenerateNewPreKeysReturning(result: Either<CoreFailure, List<PreKeyCrypto>>) {
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::generateNewPreKeys)
            .whenInvokedWith(any(), any())
            .thenReturn(result)
    }

    override fun withMostRecentPreKeyId(result: Either<StorageFailure, Int>) {
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::mostRecentPreKeyId)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withUpdatingMostRecentPrekeyReturning(result: Either<StorageFailure, Unit>) {
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::updateMostRecentPreKeyId)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withSetLastPreKeyUploadInstantReturning(result: Either<StorageFailure, Unit>) {
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::setLastPreKeyRefillCheckInstant)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withObserveLastPreKeyUploadInstantReturning(flow: Flow<Instant?>) {
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::lastPreKeyRefillCheckInstantFlow)
            .whenInvoked()
            .thenReturn(flow)
    }
}
