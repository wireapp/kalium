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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreatorTest.Arrangement.Companion.otherMembersIds
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

        coVerify {
            arrangement.persistMessage.invoke(
                matches {
                    (it.content is MessageContent.System && it.content is MessageContent.ConversationCreated)
                }
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenARemoteGroupConversation_whenPersistingAndValid_ThenShouldCreateAStartedSystemMessage() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .arrange()
        val currentTime = Clock.System.now()

        val result = sysMessageCreator.conversationStarted(
            TestUser.USER_ID,
            TestConversation.CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.GROUP),
            currentTime
        )

        result.shouldSucceed()

        coVerify {
            arrangement.persistMessage.invoke(
                matches {
                    (it.content is MessageContent.System && it.content is MessageContent.ConversationCreated && it.date == currentTime)
                }
            )
        }.wasInvoked(once)
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

        coVerify {
            arrangement.persistMessage.invoke(matches {
                (it.content is MessageContent.System && it.content is MessageContent.ConversationCreated)
            })
        }.wasNotInvoked()
    }

    @Test
    fun givenAGroupConversation_whenPersistingAndValid_ThenShouldCreateASystemMessageForReceiptStatus() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .withIsASelfTeamMember()
            .arrange()
        val timestampIso = Instant.UNIX_FIRST_DATE

        val result = sysMessageCreator.conversationReadReceiptStatus(TestConversation.CONVERSATION_RESPONSE, timestampIso)

        result.shouldSucceed()

        coVerify {
            arrangement.persistMessage.invoke(matches {
                (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode && it.date == timestampIso)
            })
        }.wasInvoked(once)
    }

    @Test
    fun givenAConversation_whenPersistingAndNotGroup_ThenShouldNOTCreateASystemMessageForReceiptStatus() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .withIsASelfTeamMember()
            .arrange()
        val timestampIso = Instant.UNIX_FIRST_DATE

        val result =
            sysMessageCreator.conversationReadReceiptStatus(
                TestConversation.CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.ONE_TO_ONE),
                timestampIso
            )

        result.shouldSucceed()

        coVerify {
            arrangement.persistMessage.invoke(matches {
                (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode && it.date == timestampIso)
            })
        }.wasNotInvoked()
    }

    @Test
    fun givenAModelGroupConversation_whenPersistingAndValid_ThenShouldCreateASystemMessageForReceiptStatus() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .withIsASelfTeamMember()
            .arrange()

        val result = sysMessageCreator.conversationReadReceiptStatus(TestConversation.CONVERSATION.copy(type = Conversation.Type.GROUP))

        result.shouldSucceed()

        coVerify {
            arrangement.persistMessage.invoke(matches {
                (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode)
            })
        }.wasInvoked(once)
    }

    @Test
    fun givenAModelConversation_whenPersistingAndNotGroup_ThenShouldNotCreateASystemMessageForReceiptStatus() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .withIsASelfTeamMember()
            .arrange()

        val result = sysMessageCreator.conversationReadReceiptStatus(TestConversation.CONVERSATION)

        result.shouldSucceed()

        coVerify {
            arrangement.persistMessage.invoke(matches {
                (it.content is MessageContent.System && it.content is MessageContent.NewConversationReceiptMode)
            })
        }.wasNotInvoked()
    }

    @Test
    fun givenNotAGroupConversation_whenPersisting_ThenShouldNOTCreateASystemMessageForReceiptStatus() =
        runTest {
            val (arrangement, sysMessageCreator) = Arrangement()
                .withPersistMessageSuccess()
                .arrange()
            val timestampIso = Instant.UNIX_FIRST_DATE

            val result = sysMessageCreator.conversationReadReceiptStatus(
                TestConversation.CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.ONE_TO_ONE),
                timestampIso
            )

            result.shouldSucceed()

            coVerify {
                arrangement.persistMessage.invoke(matches {
                    (it.content is MessageContent.System
                            && it.content is MessageContent.NewConversationReceiptMode
                            && it.date == timestampIso)
                })
            }.wasNotInvoked()
        }

    @Test
    fun givenNotAGroupConversation_whenPersistingUserNotTeamMember_ThenShouldNOTCreateASystemMessageForReceiptStatus() =
        runTest {
            val (arrangement, sysMessageCreator) = Arrangement()
                .withIsASelfTeamMember(false)
                .withPersistMessageSuccess()
                .arrange()
            val timestampIso = Clock.System.now()

            val result = sysMessageCreator.conversationReadReceiptStatus(TestConversation.CONVERSATION_RESPONSE, timestampIso)

            result.shouldSucceed()

            coVerify {
                arrangement.persistMessage.invoke(matches {
                    (it.content is MessageContent.System
                            && it.content is MessageContent.NewConversationReceiptMode
                            && it.date == timestampIso)
                })
            }.wasNotInvoked()
        }

    @Test
    fun givenAGroupConversation_whenPersistingMembers_ThenShouldCreateASystemMessageForStartedWith() =
        runTest {
            val (arrangement, sysMessageCreator) = Arrangement()
                .withIsASelfTeamMember(false)
                .withPersistMessageSuccess()
                .arrange()

            val result = sysMessageCreator.conversationResolvedMembersAdded(
                TestConversation.CONVERSATION_RESPONSE.id.toDao(),
                otherMembersIds
            )

            result.shouldSucceed()

            coVerify {
                arrangement.persistMessage.invoke(matches {
                    (it.content is MessageContent.System && it.content is MessageContent.MemberChange.CreationAdded)
                })
            }.wasInvoked(once)

            coVerify {
                arrangement.persistMessage.invoke(matches {
                    (it.content is MessageContent.System && it.content is MessageContent.MemberChange.FailedToAdd)
                })
            }.wasNotInvoked()
        }

    @Test
    fun givenAGroupConversation_whenPersistingMembersAndOtherMembersListIsEmpty_ThenShouldNOTCreateASystemMessage() =
        runTest {
            val (arrangement, sysMessageCreator) = Arrangement()
                .withIsASelfTeamMember(false)
                .withPersistMessageSuccess()
                .arrange()

            val result = sysMessageCreator.conversationResolvedMembersAdded(
                TestConversation.CONVERSATION_RESPONSE.id.toDao(),
                TestConversation.CONVERSATION_RESPONSE.copy(
                    members = ConversationMembersResponse(
                        ConversationMemberDTO.Self(
                            TestUser.SELF.id.toApi(),
                            "wire_admin"
                        ), emptyList()
                    )
                ).members.otherMembers.map { it.id.toModel() }
            )

            result.shouldSucceed()

            coVerify {
                arrangement.persistMessage.invoke(matches {
                    (it.content is MessageContent.System && it.content is MessageContent.MemberChange.CreationAdded)
                })
            }.wasNotInvoked()

            coVerify {
                arrangement.persistMessage.invoke(matches {
                    (it.content is MessageContent.System && it.content is MessageContent.MemberChange.FailedToAdd)
                })
            }.wasNotInvoked()
        }

    @Test
    fun givenConversation_whenPersistingAddingUsersFailure_ThenShouldCreateASystemMessageForThoseUsers() =
        runTest {
            val (arrangement, sysMessageCreator) = Arrangement()
                .withPersistMessageSuccess()
                .arrange()

            val result = sysMessageCreator.conversationFailedToAddMembers(
                TestConversation.ID, listOf(TestUser.OTHER.id), MessageContent.MemberChange.FailedToAdd.Type.Unknown
            )

            result.shouldSucceed()

            coVerify {
                arrangement.persistMessage.invoke(matches {
                    (it.content is MessageContent.System && it.content is MessageContent.MemberChange.FailedToAdd)
                })
            }.wasInvoked(once)
        }

    @Test
    fun givenAGroupConversation_whenPersistingAndValid_ThenShouldCreateAStartedUnverifiedSystemMessage() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .arrange()

        val result = sysMessageCreator.conversationStartedUnverifiedWarning(
            TestConversation.ENTITY.copy(type = ConversationEntity.Type.GROUP).id.toModel()
        )

        result.shouldSucceed()

        coVerify {
            arrangement.persistMessage.invoke(matches {
                (it.content is MessageContent.System && it.content is MessageContent.ConversationStartedUnverifiedWarning)
            })
        }.wasInvoked(once)
    }

    private class Arrangement {
                val persistMessage = mock(PersistMessageUseCase::class)
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)
        val qualifiedIdMapper = mock(QualifiedIdMapper::class)

        init {
            every {
                qualifiedIdMapper.fromStringToQualifiedID(any())
            }.returns(TestUser.USER_ID)
        }

        suspend fun withPersistMessageSuccess() = apply {
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withIsASelfTeamMember(isMember: Boolean = true) = apply {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(if (isMember) Either.Right(TestTeam.TEAM_ID) else Either.Right(null))
        }

        fun arrange() = this to NewGroupConversationSystemMessagesCreatorImpl(
            persistMessage,
            selfTeamIdProvider,
            qualifiedIdMapper,
            TestUser.SELF.id,
        )

        companion object {
            val otherMembersIds = TestConversation.CONVERSATION_RESPONSE.members.otherMembers.map { it.id.toModel() }
        }
    }

}
