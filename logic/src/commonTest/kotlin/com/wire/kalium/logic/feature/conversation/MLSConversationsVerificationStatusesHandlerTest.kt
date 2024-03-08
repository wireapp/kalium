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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import io.mockative.any
import io.mockative.anyInstanceOf
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MLSConversationsVerificationStatusesHandlerTest {

    @Test
    fun givenVerifiedConversation_whenVerifiedStatusComes_thenNotingChanged() = runTest {
        val conversationDetails = TestConversationDetails.CONVERSATION_GROUP.copy(
            conversation = TestConversation.MLS_CONVERSATION.copy(
                mlsVerificationStatus = Conversation.VerificationStatus.VERIFIED
            )
        )
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
            withConversationDetailsByMLSGroupId(Either.Right(conversationDetails))
        }

        handler()
        advanceUntilIdle()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMlsVerificationStatus)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setDegradedConversationNotifiedFlag)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenNotVerifiedConversation_whenNotVerifiedStatusComes_thenNothingChanged() = runTest {
        val conversationDetails = TestConversationDetails.CONVERSATION_GROUP.copy(conversation = TestConversation.MLS_CONVERSATION)
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
            withConversationDetailsByMLSGroupId(Either.Right(conversationDetails))
        }

        handler()
        advanceUntilIdle()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMlsVerificationStatus)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setDegradedConversationNotifiedFlag)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenVerifiedConversation_whenNotVerifiedStatusComes_thenStatusSetToDegradedAndSystemMessageAdded() = runTest {
        val conversationDetails = TestConversationDetails.CONVERSATION_GROUP.copy(
            conversation = TestConversation.MLS_CONVERSATION.copy(
                mlsVerificationStatus = Conversation.VerificationStatus.VERIFIED
            )
        )
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
            withConversationDetailsByMLSGroupId(Either.Right(conversationDetails))
        }

        handler()
        advanceUntilIdle()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMlsVerificationStatus)
            .with(eq(Conversation.VerificationStatus.DEGRADED), eq(conversationDetails.conversation.id))
            .wasInvoked(once)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(anyInstanceOf(Message.System::class))
            .wasInvoked(once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setDegradedConversationNotifiedFlag)
            .with(any(), eq(true))
            .wasInvoked(once)
    }

    @Test
    fun givenDegradedConversation_whenNotVerifiedStatusComes_thenNothingChanged() = runTest {
        val conversationDetails = TestConversationDetails.CONVERSATION_GROUP.copy(
            conversation = TestConversation.MLS_CONVERSATION
                .copy(mlsVerificationStatus = Conversation.VerificationStatus.DEGRADED)
        )
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.NOT_VERIFIED))
            withConversationDetailsByMLSGroupId(Either.Right(conversationDetails))
        }

        handler()
        advanceUntilIdle()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMlsVerificationStatus)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setDegradedConversationNotifiedFlag)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenDegradedConversation_whenVerifiedStatusComes_thenStatusUpdated() = runTest {
<<<<<<< HEAD
        val conversationDetails = TestConversationDetails.CONVERSATION_GROUP.copy(
            conversation = TestConversation.MLS_CONVERSATION.copy(
                mlsVerificationStatus = Conversation.VerificationStatus.DEGRADED
=======

        val user1 = UserIDEntity("user1", "domain1") to NameAndHandleEntity("name1", "device1")
        val user2 = UserIDEntity("user2", "domain2") to NameAndHandleEntity("name2", "device2")

        val epochChangedData = EpochChangesDataEntity(
            conversationId = TestConversation.CONVERSATION.id.toDao(),
            members = mapOf(user1, user2),
            mlsVerificationStatus = ConversationEntity.VerificationStatus.DEGRADED
        )

        val ccMembersIdentity: Map<UserId, List<WireIdentity>> = mapOf(
            user1.first.toModel() to listOf(
                WireIdentity(
                    CryptoQualifiedClientId(
                        value = "client_user_1",
                        userId = user1.first.toModel().toCrypto()
                    ),
                    handle = user1.second.handle!!,
                    displayName = user1.second.name!!,
                    certificate = "cert1",
                    domain = "domain1",
                    serialNumber = "serial1",
                    status = CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint1",
                    endTimestamp = 0L
                )
            ),
            user2.first.toModel() to listOf(
                WireIdentity(
                    CryptoQualifiedClientId(
                        value = "client_user_2",
                        userId = user2.first.toModel().toCrypto()
                    ),
                    handle = user2.second.handle!!,
                    displayName = user2.second.name!!,
                    certificate = "cert2",
                    domain = "domain2",
                    serialNumber = "serial2",
                    status = CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint2",
                    endTimestamp = 0L
                )
>>>>>>> 565345ade4 (fix: Suggestion to renew E2EI certificate RC (WPB-3326) (#2601))
            )
        )
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withMLSConversationVerificationStatus(Either.Right(Conversation.VerificationStatus.VERIFIED))
            withConversationDetailsByMLSGroupId(Either.Right(conversationDetails))
        }

        handler()
        advanceUntilIdle()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMlsVerificationStatus)
<<<<<<< HEAD
            .with(eq(Conversation.VerificationStatus.VERIFIED), eq(conversationDetails.conversation.id))
=======
            .with(eq(Conversation.VerificationStatus.VERIFIED), eq(epochChangedData.conversationId.toModel()))
            .wasInvoked(once)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(anyInstanceOf(Message.System::class))
            .wasInvoked(once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setDegradedConversationNotifiedFlag)
            .with(any(), eq(true))
            .wasInvoked(once)
    }

    @Test
    fun givenVerifiedConversation_whenVerifiedStatusComesAndUserNamesDivergeFromCC_thenStatusUpdatedToDegraded() = runTest {

        val user1 = UserIDEntity("user1", "domain1") to NameAndHandleEntity("name1", "device1")
        val user2 = UserIDEntity("user2", "domain2") to NameAndHandleEntity("name2", "device2")

        val epochChangedData = EpochChangesDataEntity(
            conversationId = TestConversation.CONVERSATION.id.toDao(),
            members = mapOf(user1, user2),
            mlsVerificationStatus = ConversationEntity.VerificationStatus.VERIFIED
        )

        val ccMembersIdentity: Map<UserId, List<WireIdentity>> = mapOf(
            user1.first.toModel() to listOf(
                WireIdentity(
                    CryptoQualifiedClientId(
                        value = "client_user_1",
                        userId = user1.first.toModel().toCrypto()
                    ),
                    handle = user1.second.handle!! + "1", // this user name changed
                    displayName = user1.second.name!! + "1",
                    certificate = "cert1",
                    domain = "domain1",
                    serialNumber = "serial1",
                    status = CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint1",
                    endTimestamp = 0L
                )
            ),
            user2.first.toModel() to listOf(
                WireIdentity(
                    CryptoQualifiedClientId(
                        value = "client_user_2",
                        userId = user2.first.toModel().toCrypto()
                    ),
                    handle = user2.second.handle!!,
                    displayName = user2.second.name!!,
                    certificate = "cert2",
                    domain = "domain2",
                    serialNumber = "serial2",
                    status = CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint2",
                    endTimestamp = 0L
                )
            )
        )
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withIsGroupVerified(E2EIConversationState.VERIFIED)
            withSelectGroupStatusMembersNamesAndHandles(Either.Right(epochChangedData))
            withGetMembersIdentities(Either.Right(ccMembersIdentity))
        }

        handler()
        advanceUntilIdle()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMlsVerificationStatus)
            .with(eq(Conversation.VerificationStatus.DEGRADED), eq(epochChangedData.conversationId.toModel()))
>>>>>>> 565345ade4 (fix: Suggestion to renew E2EI certificate RC (WPB-3326) (#2601))
            .wasInvoked(once)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(anyInstanceOf(Message.System::class))
            .wasInvoked(once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::setDegradedConversationNotifiedFlag)
            .with(any(), eq(false))
            .wasInvoked(once)
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl() {

        init {
            withUpdateVerificationStatus(Either.Right(Unit))
            withPersistingMessage(Either.Right(Unit))
            withSetDegradedConversationNotifiedFlag(Either.Right(Unit))
        }

        fun arrange() = block().run {
            this@Arrangement to MLSConversationsVerificationStatusesHandlerImpl(
                conversationRepository = conversationRepository,
                persistMessage = persistMessageUseCase,
                conversationVerificationStatusChecker = conversationVerificationStatusChecker,
                epochChangesObserver = epochChangesObserver,
                selfUserId = TestUser.USER_ID,
                kaliumLogger = kaliumLogger
            )
        }
    }
}
