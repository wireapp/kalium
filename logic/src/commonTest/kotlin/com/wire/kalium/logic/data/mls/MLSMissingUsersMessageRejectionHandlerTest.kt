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
package com.wire.kalium.logic.data.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MLSMissingUsersMessageRejectionHandlerTest {

    private val cryptoTransactionProviderArrangement: CryptoTransactionProviderArrangement = CryptoTransactionProviderArrangementImpl()

    @Test
    fun givenConversationIsNotMLS_whenHandling_thenShouldReturnConversationDoesNotSupportMLS() = runTest {
        val memberAdder = MLSMemberAdder { _, _, _, _ -> throw IllegalStateException("This should not be called in this test") }
        val subject = MLSMissingUsersMessageRejectionHandlerImpl(
            mlsMemberAdder = memberAdder,
            protocolGetter = { _ -> Either.Right(Conversation.ProtocolInfo.Proteus) },
            logger = KaliumLogger.disabled()
        )

        subject.handle(
            cryptoTransactionProviderArrangement.transactionContext,
            MockConversation.ID,
            GroupID("test"),
            NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync(
                listOf(TestUser.OTHER_USER_ID)
            )
        ).shouldFail { failure ->
            assertIs<MLSFailure.ConversationDoesNotSupportMLS>(failure)
        }
    }

    @Test
    fun givenEmptyMissingUserList_whenHandling_thenShouldReturnSucceed() = runTest {
        val memberAdder = MLSMemberAdder { _, _, _, _ -> throw IllegalStateException("This should not be called in this test") }
        val subject = MLSMissingUsersMessageRejectionHandlerImpl(
            mlsMemberAdder = memberAdder,
            protocolGetter = { _ -> throw IllegalStateException("This should not be called in this test") },
            logger = KaliumLogger.disabled()
        )

        subject.handle(
            cryptoTransactionProviderArrangement.transactionContext,
            MockConversation.ID,
            GroupID("test"),
            NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync(
                emptyList()
            )
        ).shouldSucceed()
    }

    @Test
    fun givenGettingProtocolFails_whenHandling_thenShouldBubbleUpTheFailure() = runTest {
        val expectedFailure = CoreFailure.DevelopmentAPINotAllowedOnProduction
        val memberAdder = MLSMemberAdder { _, _, _, _ -> throw IllegalStateException("This should not be called in this test") }
        val groupID = GroupID("test")
        val subject = MLSMissingUsersMessageRejectionHandlerImpl(
            mlsMemberAdder = memberAdder,
            protocolGetter = { _ -> Either.Left(expectedFailure) },
            logger = KaliumLogger.disabled()
        )

        subject.handle(
            cryptoTransactionProviderArrangement.transactionContext,
            MockConversation.ID,
            groupID,
            NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync(
                listOf(TestUser.OTHER_USER_ID)
            )
        ).shouldFail { failure ->
            assertEquals(expectedFailure, failure)
        }
    }

    @Test
    fun givenMLSCapableConversationAndUserList_whenHandling_thenShouldAttemptToAddMembers() = runTest {
        var memberAddedCalled = false
        val memberAdder = MLSMemberAdder { _, _, _, _ -> Either.Right(Unit).also { memberAddedCalled = true } }
        val groupID = GroupID("test")
        val subject = MLSMissingUsersMessageRejectionHandlerImpl(
            mlsMemberAdder = memberAdder,
            protocolGetter = { _ ->
                Either.Right(
                    Conversation.ProtocolInfo.MLS(
                        groupID,
                        Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                        0UL,
                        Clock.System.now(),
                        CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
                    )
                )
            },
            logger = KaliumLogger.disabled()
        )

        subject.handle(
            cryptoTransactionProviderArrangement.transactionContext,
            MockConversation.ID,
            groupID,
            NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync(
                listOf(TestUser.OTHER_USER_ID)
            )
        ).shouldSucceed()

        assertTrue { memberAddedCalled }
    }

    @Test
    fun givenAddingMemberFails_whenHandling_thenShouldBubbleUpTheFailure() = runTest {
        val expectedFailure = CoreFailure.DevelopmentAPINotAllowedOnProduction
        val memberAdder = MLSMemberAdder { _, _, _, _ -> Either.Left(expectedFailure) }
        val groupID = GroupID("test")
        val subject = MLSMissingUsersMessageRejectionHandlerImpl(
            mlsMemberAdder = memberAdder,
            protocolGetter = { _ ->
                Either.Right(
                    Conversation.ProtocolInfo.MLS(
                        groupID,
                        Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                        0UL,
                        Clock.System.now(),
                        CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
                    )
                )
            },
            logger = KaliumLogger.disabled()
        )

        subject.handle(
            cryptoTransactionProviderArrangement.transactionContext,
            MockConversation.ID,
            groupID,
            NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync(
                listOf(TestUser.OTHER_USER_ID)
            )
        ).shouldFail { failure ->
            assertEquals(expectedFailure, failure)
        }
    }

}
