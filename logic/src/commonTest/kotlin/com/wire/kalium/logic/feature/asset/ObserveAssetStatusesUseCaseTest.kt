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
package com.wire.kalium.logic.feature.asset

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.MessageAssetStatus
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAssetStatusesUseCaseTest {

    @BeforeTest
    fun before() {
        Dispatchers.setMain(testDispatcher.default)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenStorageFailure_whenObservingAssetStatuses_thenEmptyMapShouldReturned() = runTest {
        val (arrangement, observeAssetStatuses) = Arrangement()
            .withAssetStatuses(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        observeAssetStatuses.invoke(TestConversation.ID).test {
            val result = awaitItem()
            assertTrue(result.isEmpty())
            awaitComplete()
        }
    }

    @Test
    fun givenAssetStatusList_whenObservingAssetStatuses_thenStatusesShouldBeProperlyMapped() = runTest {
        val (arrangement, observeAssetStatuses) = Arrangement()
            .withAssetStatuses(
                Either.Right(
                    listOf(
                        MessageAssetStatus("id1", TestConversation.ID, AssetTransferStatus.UPLOADED),
                        MessageAssetStatus("id2", TestConversation.ID, AssetTransferStatus.NOT_DOWNLOADED),
                        MessageAssetStatus("id3", TestConversation.ID, AssetTransferStatus.SAVED_INTERNALLY)
                    )
                )
            )
            .arrange()

        observeAssetStatuses.invoke(TestConversation.ID).test {
            val result = awaitItem()
            val assetStatusMap = result.mapValues { it.value.transferStatus }
            assertTrue(assetStatusMap.containsValue(AssetTransferStatus.UPLOADED))
            assertTrue(assetStatusMap.containsValue(AssetTransferStatus.NOT_DOWNLOADED))
            assertTrue(assetStatusMap.containsValue(AssetTransferStatus.SAVED_INTERNALLY))
            awaitComplete()
        }
    }

    private class Arrangement {
        val messageRepository = mock(MessageRepository::class)

        suspend fun withAssetStatuses(result: Either<StorageFailure, List<MessageAssetStatus>>) = apply {
            coEvery {
                messageRepository.observeAssetStatuses(any())
            }.returns(flowOf(result))
        }

        fun arrange() = this to ObserveAssetStatusesUseCaseImpl(messageRepository, testDispatcher)
    }

    private companion object {
        val testDispatcher = TestKaliumDispatcher
    }
}
