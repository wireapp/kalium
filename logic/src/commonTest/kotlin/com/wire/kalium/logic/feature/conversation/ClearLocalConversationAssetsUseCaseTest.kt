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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class ClearLocalConversationAssetsUseCaseTest {

    @Test
    fun givenConversationAssetIds_whenAllDeletionsAreSuccess_thenSuccessResultIsPropagated() = runTest {
        // given
        val ids = listOf("id_1", "id_2")
        val (arrangement, useCase) = Arrangement()
            .withAssetIdsResponse(ids)
            .withAssetClearSuccess("id_1")
            .withAssetClearSuccess("id_2")
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<Either.Right<Unit>>(result)
        coVerify { arrangement.assetRepository.deleteAssetLocally(any()) }.wasInvoked(exactly = 2)
    }

    @Test
    fun givenConversationAssetIds_whenOneDeletionFailed_thenFailureResultIsPropagated() = runTest {
        // given
        val ids = listOf("id_1", "id_2")
        val (arrangement, useCase) = Arrangement()
            .withAssetIdsResponse(ids)
            .withAssetClearSuccess("id_1")
            .withAssetClearError("id_2")
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<Either.Left<Unit>>(result)
        coVerify { arrangement.assetRepository.deleteAssetLocally(any()) }.wasInvoked(exactly = 2)
    }

    @Test
    fun givenEmptyConversationAssetIds_whenInvoked_thenDeletionsAreNotInvoked() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withAssetIdsResponse(emptyList())
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        assertIs<Either.Right<Unit>>(result)
        coVerify { arrangement.assetRepository.deleteAssetLocally(any()) }.wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val messageRepository = mock(MessageRepository::class)

        @Mock
        val assetRepository = mock(AssetRepository::class)

        suspend fun withAssetClearSuccess(id: String) = apply {
            coEvery { assetRepository.deleteAssetLocally(id) }.returns(Either.Right(Unit))
        }

        suspend fun withAssetClearError(id: String) = apply {
            coEvery { assetRepository.deleteAssetLocally(id) }.returns(Either.Left(CoreFailure.Unknown(null)))
        }

        suspend fun withAssetIdsResponse(ids: List<String>) = apply {
            coEvery { messageRepository.getAllAssetIdsFromConversationId(any()) }.returns(Either.Right(ids))
        }

        fun arrange() = this to ClearLocalConversationAssetsUseCaseImpl(
            messageRepository = messageRepository,
            assetRepository = assetRepository
        )
    }
}
