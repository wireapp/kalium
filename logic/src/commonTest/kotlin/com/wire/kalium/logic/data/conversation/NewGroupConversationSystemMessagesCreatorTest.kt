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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.ConversationEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NewGroupConversationSystemMessagesCreatorTest {

    @Test
    fun givenAGroupConversation_whenPersistingAndValid_ThenShouldCreateAStartedSystemMessage() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .arrange()

        val result = sysMessageCreator.conversationStarted(
            TestConversation.ENTITY.copy(type = ConversationEntity.Type.GROUP)
        )

        result.shouldSucceed()

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content is MessageContent.System && it.content is MessageContent.ConversationCreated)
            })
            .wasInvoked(once)
    }

    @Test
    fun givenARemoteGroupConversation_whenPersistingAndValid_ThenShouldCreateAStartedSystemMessage() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .arrange()

        val result = sysMessageCreator.conversationStarted(
            TestUser.USER_ID,
            TestConversation.CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.GROUP)
        )

        result.shouldSucceed()

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content is MessageContent.System && it.content is MessageContent.ConversationCreated)
            })
            .wasInvoked(once)
    }


    @Test
    fun givenNotAGroupConversation_whenPersisting_ThenShouldNOTCreateAStartedSystemMessage() = runTest {
        val (arrangement, newGroupConversationCreatedHandler) = Arrangement()
            .withPersistMessageSuccess()
            .arrange()

        val result = newGroupConversationCreatedHandler.conversationStarted(
            TestConversation.ENTITY.copy(type = ConversationEntity.Type.ONE_ON_ONE)
        )

        result.shouldSucceed()

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content is MessageContent.System && it.content is MessageContent.ConversationCreated)
            })
            .wasNotInvoked()
    }

    @Test
    fun givenAGroupConversation_whenPersistingAndValid_ThenShouldCreateASystemMessageForReceiptStatus() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .withIsASelfTeamMember()
            .arrange()

        val result = sysMessageCreator.conversationReadReceiptStatus(TestConversation.CONVERSATION_RESPONSE)

        result.shouldSucceed()

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode)
            })
            .wasInvoked(once)
    }

    @Test
    fun givenAConversation_whenPersistingAndNotGroup_ThenShouldNOTCreateASystemMessageForReceiptStatus() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .withIsASelfTeamMember()
            .arrange()

        val result =
            sysMessageCreator.conversationReadReceiptStatus(TestConversation.CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.ONE_TO_ONE))

        result.shouldSucceed()

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode)
            })
            .wasNotInvoked()
    }


    @Test
    fun givenAModelGroupConversation_whenPersistingAndValid_ThenShouldCreateASystemMessageForReceiptStatus() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .withIsASelfTeamMember()
            .arrange()

        val result = sysMessageCreator.conversationReadReceiptStatus(TestConversation.CONVERSATION.copy(type = Conversation.Type.GROUP))

        result.shouldSucceed()

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode)
            })
            .wasInvoked(once)
    }

    @Test
    fun givenAModelConversation_whenPersistingAndNotGroup_ThenShouldNotCreateASystemMessageForReceiptStatus() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .withIsASelfTeamMember()
            .arrange()

        val result = sysMessageCreator.conversationReadReceiptStatus(TestConversation.CONVERSATION)

        result.shouldSucceed()

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode)
            })
            .wasNotInvoked()
    }

    @Test
    fun givenNotAGroupConversation_whenPersisting_ThenShouldNOTCreateASystemMessageForReceiptStatus() =
        runTest {
            val (arrangement, sysMessageCreator) = Arrangement()
                .withPersistMessageSuccess()
                .arrange()

            val result = sysMessageCreator.conversationReadReceiptStatus(
                TestConversation.CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.ONE_TO_ONE)
            )

            result.shouldSucceed()

            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(matching {
                    (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode)
                })
                .wasNotInvoked()
        }

    @Test
    fun givenNotAGroupConversation_whenPersistingUserNotTeamMember_ThenShouldNOTCreateASystemMessageForReceiptStatus() =
        runTest {
            val (arrangement, sysMessageCreator) = Arrangement()
                .withIsASelfTeamMember(false)
                .withPersistMessageSuccess()
                .arrange()

            val result = sysMessageCreator.conversationReadReceiptStatus(TestConversation.CONVERSATION_RESPONSE)

            result.shouldSucceed()

            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(matching {
                    (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode)
                })
                .wasNotInvoked()
        }

    @Test
    fun givenAGroupConversation_whenPersistingMembers_ThenShouldCreateASystemMessageForStartedWith() =
        runTest {
            val (arrangement, sysMessageCreator) = Arrangement()
                .withIsASelfTeamMember(false)
                .withPersistMessageSuccess()
                .arrange()

            val result = sysMessageCreator.conversationResolvedMembersAddedAndFailed(
                TestConversation.CONVERSATION_RESPONSE.id.toDao(),
                TestConversation.CONVERSATION_RESPONSE
            )

            result.shouldSucceed()

            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(matching {
                    (it.content is MessageContent.System && it.content is MessageContent.MemberChange.CreationAdded)
                })
                .wasInvoked(once)
        }

    @Test
    fun givenAGroupConversation_whenPersistingMembersAndSomeFailed_ThenShouldCreateASystemMessageForStartedWithAndFailedToAdd() =
        runTest {
            val (arrangement, sysMessageCreator) = Arrangement()
                .withIsASelfTeamMember(false)
                .withPersistMessageSuccess()
                .arrange()

            val result = sysMessageCreator.conversationResolvedMembersAddedAndFailed(
                TestConversation.CONVERSATION_RESPONSE.id.toDao(),
                TestConversation.CONVERSATION_RESPONSE.copy(failedToAdd = setOf(TestUser.USER_ID.toApi()))
            )

            result.shouldSucceed()

            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(matching {
                    (it.content is MessageContent.System && it.content is MessageContent.MemberChange.CreationAdded)
                })
                .wasInvoked(once)

            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(matching {
                    (it.content is MessageContent.System && it.content is MessageContent.MemberChange.FailedToAdd)
                })
                .wasInvoked(once)
        }


    private class Arrangement {
        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val isSelfATeamMember = mock(IsSelfATeamMemberUseCase::class)

        @Mock
        val qualifiedIdMapper = mock(QualifiedIdMapper::class)

        init {
            given(qualifiedIdMapper)
                .function(qualifiedIdMapper::fromStringToQualifiedID)
                .whenInvokedWith(any())
                .then { TestUser.USER_ID }
        }

        fun withPersistMessageSuccess() = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .then { Either.Right(Unit) }
        }

        fun withIsASelfTeamMember(isMember: Boolean = true) = apply {
            given(isSelfATeamMember)
                .suspendFunction(isSelfATeamMember::invoke)
                .whenInvoked()
                .then { isMember }
        }

        fun arrange() = this to NewGroupConversationSystemMessagesCreatorImpl(
            persistMessage,
            isSelfATeamMember,
            qualifiedIdMapper,
            TestUser.SELF.id,
        )
    }

}
