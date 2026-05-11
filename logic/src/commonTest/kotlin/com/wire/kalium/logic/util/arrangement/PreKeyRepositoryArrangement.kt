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
package com.wire.kalium.logic.util.arrangement

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.common.functional.Either
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

internal interface PreKeyRepositoryArrangement {
    val preKeyRepository: PreKeyRepository

    suspend fun withRemotelyAvailablePreKeysReturning(result: Either<CoreFailure, List<Int>>)

    suspend fun withUploadNewPrekeyBatchReturning(result: Either<CoreFailure, Unit>)

    suspend fun withGenerateNewPreKeysReturning(result: Either<CoreFailure, List<PreKeyCrypto>>)

    suspend fun withMostRecentPreKeyId(result: Either<StorageFailure, Int>)

    suspend fun withUpdatingMostRecentPrekeyReturning(result: Either<StorageFailure, Unit>)

    suspend fun withSetLastPreKeyUploadInstantReturning(result: Either<StorageFailure, Unit>)

    suspend fun withObserveLastPreKeyUploadInstantReturning(flow: Flow<Instant?>)
}

internal class PreKeyRepositoryArrangementImpl : PreKeyRepositoryArrangement {

    override val preKeyRepository: PreKeyRepository = mock<PreKeyRepository>(mode = MockMode.autoUnit)

    override suspend fun withRemotelyAvailablePreKeysReturning(result: Either<CoreFailure, List<Int>>) {
        everySuspend {
            preKeyRepository.fetchRemotelyAvailablePrekeys()
        }.returns(result)
    }

    override suspend fun withUploadNewPrekeyBatchReturning(result: Either<CoreFailure, Unit>) {
        everySuspend {
            preKeyRepository.uploadNewPrekeyBatch(any())
        }.returns(result)
    }

    override suspend fun withGenerateNewPreKeysReturning(result: Either<CoreFailure, List<PreKeyCrypto>>) {
        everySuspend {
            preKeyRepository.generateNewPreKeys(any(), any())
        }.returns(result)
    }

    override suspend fun withMostRecentPreKeyId(result: Either<StorageFailure, Int>) {
        everySuspend {
            preKeyRepository.mostRecentPreKeyId()
        }.returns(result)
    }

    override suspend fun withUpdatingMostRecentPrekeyReturning(result: Either<StorageFailure, Unit>) {
        everySuspend {
            preKeyRepository.updateMostRecentPreKeyId(any())
        }.returns(result)
    }

    override suspend fun withSetLastPreKeyUploadInstantReturning(result: Either<StorageFailure, Unit>) {
        everySuspend {
            preKeyRepository.setLastPreKeyRefillCheckInstant(any())
        }.returns(result)
    }

    override suspend fun withObserveLastPreKeyUploadInstantReturning(flow: Flow<Instant?>) {
        everySuspend {
            preKeyRepository.lastPreKeyRefillCheckInstantFlow()
        }.returns(flow)
    }
}
