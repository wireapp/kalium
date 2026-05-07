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

import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertContains

class GetAssetMessagesForConversationUseCaseTest {

    @Test
    fun givenConversationId_whenFetchingAssetMessages_thenShouldReturnCorrectAssets() = runTest(testDispatcher.default) {
        // Given
        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val limit = 20
        val offset = 0

        val assetMessage = AssetMessage(
            time = Instant.fromEpochMilliseconds(10),
            conversationId = someConversationId,
            username = "username",
            messageId = "messageId",
            assetId = "assetId",
            width = 640,
            height = 480,
            assetPath = "asset/path".toPath(),
            isSelfAsset = false
        )

        val (arrangement, getAssetMessages) = Arrangement()
            .withAssetMessages(
                listOf(
                    assetMessage
                ),
                someConversationId,
                limit,
                offset
            )
            .arrange()

        // When
        val result = getAssetMessages(someConversationId, limit, offset)
        assertContains(result, assetMessage)

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.getImageAssetMessagesByConversationId(someConversationId, limit, offset)
        }
    }

    private class Arrangement {
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)

        val getAssetMessagesByConversationUseCase = GetImageAssetMessagesForConversationUseCaseImpl(
            testDispatcher,
            messageRepository
        )

        suspend fun withAssetMessages(
            assetList: List<AssetMessage>,
            conversationId: ConversationId,
            limit: Int,
            offset: Int
        ): Arrangement = apply {
            everySuspend {
                messageRepository.getImageAssetMessagesByConversationId(conversationId, limit, offset)
            } returns assetList
        }

        fun arrange() = this to getAssetMessagesByConversationUseCase
    }

    private companion object {
        val testDispatcher = TestKaliumDispatcher
    }
}
