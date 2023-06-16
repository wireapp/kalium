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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.Times
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ClearConversationContentTest {

    @Test
    fun givenConversationHavingAssetMessagesAndTextMessages_whenInvoking_thenThoseAssetsWithMessagesAreRemoved() = runTest {
        // given
        val (arrangement, clearConversationContent) = Arrangement()
            .withDeleteAsset(true)
            .withDeleteAllMessages(ConversationId("someValue", "someDomain"), true)
            .withGetAssetMessages(
                listOf(
                    TestMessage.assetMessage("1"),
                    TestMessage.assetMessage("2"),
                    TestMessage.assetMessage("3")
                )
            ).arrange()

        // when
        val result = clearConversationContent(ConversationId("someValue", "someDomain"))

        // then
        with(arrangement) {
            verify(assetRepository)
                .suspendFunction(assetRepository::deleteAssetLocally)
                .with(anything())
                .wasInvoked(Times(3))
        }

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenConversationHasNoAssetsButOnlyTextMessages_whenInvoking_thenNoAssetsAreRemoved() = runTest {
        // given
        val (arrangement, clearConversationContent) = Arrangement()
            .withDeleteAsset(true)
            .withDeleteAllMessages(ConversationId("someValue", "someDomain"), true)
            .withGetAssetMessages(emptyList())
            .arrange()

        // when
        val result = clearConversationContent(ConversationId("someValue", "someDomain"))

        // then
        with(arrangement) {
            verify(assetRepository)
                .suspendFunction(assetRepository::deleteAsset)
                .with(anything(), anything(), anything())
                .wasNotInvoked()
        }

        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenGettingAssetsMessagesFails_whenInvoking_thenFailureIsCorrectlyPropagated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessages(emptyList(), false)
            .withDeleteAsset(true)
            .withDeleteAllMessages(ConversationId("someValue", "someDomain"), true)
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        with(arrangement) {
            verify(assetRepository)
                .suspendFunction(assetRepository::deleteAsset)
                .with(anything(), anything(), anything())
                .wasNotInvoked()
        }

        assertIs<Either.Left<CoreFailure>>(result)
    }

    @Test
    fun givenDeletingAllMessagesFails_whenInvoking_thenFailureIsCorrectlyPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withDeleteAllMessages(ConversationId("someValue", "someDomain"), false)
            .withDeleteAsset(true)
            .withGetAssetMessages(
                listOf(
                    TestMessage.assetMessage("1"),
                    TestMessage.assetMessage("2"),
                    TestMessage.assetMessage("3")
                ), true
            )
            .arrange()

        // when
        val result = useCase(ConversationId("someValue", "someDomain"))

        // then
        with(arrangement) {
            verify(assetRepository)
                .suspendFunction(assetRepository::deleteAsset)
                .with(anything(), anything(), anything())
                .wasNotInvoked()

            verify(conversationRepository)
                .suspendFunction(conversationRepository::deleteAllMessages)
                .with(anything())
                .wasInvoked(exactly = once)

            verify(conversationRepository)
                .suspendFunction(conversationRepository::getAssetMessages)
                .with(anything())
                .wasInvoked(exactly = once)
        }

        assertIs<Either.Left<CoreFailure>>(result)
    }

    private class Arrangement {

        @Mock
        val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val assetRepository: AssetRepository = mock(classOf<AssetRepository>())

        fun withGetAssetMessages(assetMessages: List<Message> = emptyList(), isSuccessFull: Boolean = true): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getAssetMessages)
                .whenInvokedWith(anything())
                .thenReturn(if (isSuccessFull) Either.Right(assetMessages) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun withDeleteAsset(isSuccessFull: Boolean): Arrangement {
            given(assetRepository)
                .suspendFunction(assetRepository::deleteAssetLocally)
                .whenInvokedWith(anything())
                .thenReturn(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun withDeleteAllMessages(conversationId: ConversationId, isSuccessFull: Boolean): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::deleteAllMessages)
                .whenInvokedWith(anything())
                .thenReturn(if (isSuccessFull) Either.Right(Unit) else Either.Left(CoreFailure.Unknown(Throwable("an error"))))

            return this
        }

        fun arrange() = this to
                ClearConversationContentImpl(conversationRepository, assetRepository)

    }

}
