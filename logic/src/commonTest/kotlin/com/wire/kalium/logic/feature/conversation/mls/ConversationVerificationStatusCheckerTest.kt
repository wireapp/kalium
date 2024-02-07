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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationVerificationStatusCheckerTest {

    @Test
    fun givenVerifiedConversation_whenGetGroupVerify_thenVerifiedReturned() = runTest {
        val (arrangement, conversationVerificationStatusChecker) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetGroupVerifyReturn(E2EIConversationState.VERIFIED)
            .arrange()

        assertEquals(
            Either.Right(Conversation.VerificationStatus.VERIFIED),
            conversationVerificationStatusChecker.check(GROUP_ID)
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::isGroupVerified)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenNotVerifiedConversation_whenGetGroupVerify_thenNotVerifiedReturned() = runTest {
        val (arrangement, conversationVerificationStatusChecker) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetGroupVerifyReturn(E2EIConversationState.NOT_VERIFIED)
            .arrange()

        assertEquals(
            Either.Right(Conversation.VerificationStatus.NOT_VERIFIED),
            conversationVerificationStatusChecker.check(GROUP_ID)
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::isGroupVerified)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenNotEnabledE2EIForConversation_whenGetGroupVerify_thenNotVerifiedReturned() = runTest {
        val (arrangement, conversationVerificationStatusChecker) = Arrangement()
            .withGetMLSClientSuccessful()
            .withGetGroupVerifyReturn(E2EIConversationState.NOT_ENABLED)
            .arrange()

        assertEquals(
            Either.Right(Conversation.VerificationStatus.NOT_VERIFIED),
            conversationVerificationStatusChecker.check(GROUP_ID)
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::isGroupVerified)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenNoMLSClient_whenGetGroupVerify_thenErrorReturned() = runTest {
        val failure = CoreFailure.Unknown(RuntimeException("Error!"))
        val (arrangement, conversationVerificationStatusChecker) = Arrangement()
            .withGetMLSClientFailed(failure)
            .withGetGroupVerifyReturn(E2EIConversationState.NOT_VERIFIED)
            .arrange()

        assertEquals(
            Either.Left(failure),
            conversationVerificationStatusChecker.check(GROUP_ID)
        )

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::isGroupVerified)
            .with(any())
            .wasNotInvoked()
    }

    internal class Arrangement {

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        fun arrange() = this to ConversationVerificationStatusCheckerImpl(
            mlsClientProvider = mlsClientProvider
        )

        fun withGetMLSClientSuccessful() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Right(mlsClient) }
        }

        fun withGetGroupVerifyReturn(verificationStatus: E2EIConversationState) = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::isGroupVerified)
                .whenInvokedWith(anything())
                .thenReturn(verificationStatus)
        }

        fun withGetMLSClientFailed(failure: CoreFailure.Unknown) = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Left(failure) }
        }
    }

    companion object {
        private const val RAW_GROUP_ID = "groupId"
        val GROUP_ID = GroupID(RAW_GROUP_ID)

    }
}
