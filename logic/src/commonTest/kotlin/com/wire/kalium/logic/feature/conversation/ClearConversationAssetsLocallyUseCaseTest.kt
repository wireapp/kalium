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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.common.functional.Either
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class ClearConversationAssetsLocallyUseCaseTest {

    @Test
    fun givenConversationAssetIds_whenAllDeletionsAreSuccess_thenSuccessResultIsPropagated() = runTest {
        val ids = listOf("id_1", "id_2")
        val (arrangement, useCase) = Arrangement()
            .withAssetIdsResponse(ids)
            .withAssetClearSuccess("id_1")
            .withAssetClearSuccess("id_2")
            .arrange()

        val result = useCase(ConversationId("someValue", "someDomain"))

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.exactly(2)) { arrangement.assetRepository.deleteAssetLocally(any()) }
    }

    @Test
    fun givenConversationAssetIds_whenOneDeletionFailed_thenFailureResultIsPropagated() = runTest {
        val ids = listOf("id_1", "id_2")
        val (arrangement, useCase) = Arrangement()
            .withAssetIdsResponse(ids)
            .withAssetClearSuccess("id_1")
            .withAssetClearError("id_2")
            .arrange()

        val result = useCase(ConversationId("someValue", "someDomain"))

        assertIs<Either.Left<Unit>>(result)
        verifySuspend(VerifyMode.exactly(2)) { arrangement.assetRepository.deleteAssetLocally(any()) }
    }

    @Test
    fun givenEmptyConversationAssetIds_whenInvoked_thenDeletionsAreNotInvoked() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withAssetIdsResponse(emptyList())
            .arrange()

        val result = useCase(ConversationId("someValue", "someDomain"))

        assertIs<Either.Right<Unit>>(result)
        verifySuspend(VerifyMode.not) { arrangement.assetRepository.deleteAssetLocally(any()) }
    }

    private class Arrangement {
        
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val assetRepository = mock<AssetRepository>(mode = MockMode.autoUnit)

        suspend fun withAssetClearSuccess(id: String) = apply {
            everySuspend { assetRepository.deleteAssetLocally(id) } returns Either.Right(Unit)
        }

        suspend fun withAssetClearError(id: String) = apply {
            everySuspend { assetRepository.deleteAssetLocally(id) } returns Either.Left(CoreFailure.Unknown(null))
        }

        suspend fun withAssetIdsResponse(ids: List<String>) = apply {
            everySuspend { messageRepository.getAllAssetIdsFromConversationId(any()) } returns Either.Right(ids)
        }

        fun arrange() = this to ClearConversationAssetsLocallyUseCaseImpl(
            messageRepository = messageRepository,
            assetRepository = assetRepository
        )
    }
}
