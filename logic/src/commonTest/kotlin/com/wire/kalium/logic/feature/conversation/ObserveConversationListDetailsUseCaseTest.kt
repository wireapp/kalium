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

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LongMethod")
@Ignore // TODO
class ObserveConversationListDetailsUseCaseTest {

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenObserveConversationListShouldBeCalled() = runTest {
        // Given
        val groupConversation = TestConversation.GROUP()
        val selfConversation = TestConversation.SELF()
        val conversations = listOf(selfConversation, groupConversation)
        val selfConversationDetails = ConversationDetails.Self(selfConversation)
        val fetchArchivedConversations = false
        val groupConversationDetails =
            ConversationDetails.Group(
                groupConversation,
                lastMessage = null,
                isSelfUserMember = true,
                isSelfUserCreator = true,
                unreadEventCount = emptyMap(),
                selfRole = Conversation.Member.Role.Member
            )

        val (arrangement, observeConversationsUseCase) = Arrangement()
            .withConversationsList(conversations)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .withSuccessfulConversationsDetailsListUpdates(selfConversation, listOf(selfConversationDetails))
            .arrange()

        // When
        observeConversationsUseCase(fetchArchivedConversations).collect()

        // Then
        with(arrangement) {
            coVerify {
                conversationRepository.observeConversationList()
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSomeConversationsWithArchivedValues_whenFetchingOnlyNonArchived_thenTheseConversationsShouldNotBeReturned() = runTest {
        // Given
        val groupConversation1 = TestConversation.GROUP().copy(archived = true)
        val group2Id = ConversationId("OtherId", "someDomain")
        val groupConversation2 = TestConversation.GROUP().copy(id = group2Id, archived = false)
        val selfConversation = TestConversation.SELF()
        val conversations = listOf(selfConversation, groupConversation1, groupConversation2)
        val selfConversationDetails = ConversationDetails.Self(selfConversation)
        val fetchArchivedConversations = false
        val groupConversationDetails1 =
            ConversationDetails.Group(
                groupConversation1,
                lastMessage = null,
                isSelfUserMember = true,
                isSelfUserCreator = true,
                unreadEventCount = emptyMap(),
                selfRole = Conversation.Member.Role.Member
            )
        val groupConversationDetails2 =
            ConversationDetails.Group(
                groupConversation2,
                lastMessage = null,
                isSelfUserMember = true,
                isSelfUserCreator = true,
                unreadEventCount = emptyMap(),
                selfRole = Conversation.Member.Role.Member
            )

        val (arrangement, observeConversationsUseCase) = Arrangement()
            .withConversationsList(conversations)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation1, listOf(groupConversationDetails1))
            .withSuccessfulConversationsDetailsListUpdates(groupConversation2, listOf(groupConversationDetails2))
            .withSuccessfulConversationsDetailsListUpdates(selfConversation, listOf(selfConversationDetails))
            .arrange()

        // When
        observeConversationsUseCase(fetchArchivedConversations).test {
            val conversationDetailsList = awaitItem()

            // Then
            assertFalse(conversationDetailsList.contains(groupConversationDetails2))
            assertTrue(conversationDetailsList.contains(selfConversationDetails))
            assertTrue(conversationDetailsList.contains(groupConversationDetails1))
        }
    }

    @Test
    fun givenSomeConversations_whenObservingDetailsList_thenObserveConversationDetailsShouldBeCalledForEachID() = runTest {
        // Given
        val selfConversation = TestConversation.SELF()
        val groupConversation = TestConversation.GROUP()
        val conversations = listOf(selfConversation, groupConversation)
        val fetchArchivedConversations = false

        val selfConversationDetails = ConversationDetails.Self(selfConversation)
        val groupConversationDetails = ConversationDetails.Group(
            conversation = groupConversation,
            lastMessage = null,
            isSelfUserMember = true,
            isSelfUserCreator = true,
            unreadEventCount = emptyMap(),
            selfRole = Conversation.Member.Role.Member
        )

        val (arrangement, observeConversationsUseCase) = Arrangement()
            .withConversationsList(conversations)
            .withSuccessfulConversationsDetailsListUpdates(selfConversation, listOf(selfConversationDetails))
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .arrange()

        // When
        observeConversationsUseCase(fetchArchivedConversations).collect()

        with(arrangement) {
            conversations.forEach { conversation ->
                coVerify {
                    conversationRepository.observeConversationDetailsById(eq(conversation.id))
                }.wasInvoked(exactly = once)
            }
        }
    }

    @Test
    fun givenSomeConversationsDetailsAreUpdated_whenObservingDetailsList_thenTheUpdateIsPropagatedThroughTheFlow() = runTest {
        // Given
        val oneOnOneConversation = TestConversation.ONE_ON_ONE()
        val groupConversation = TestConversation.GROUP()
        val conversations = listOf(groupConversation, oneOnOneConversation)
        val fetchArchivedConversations = false

        val groupConversationUpdates = listOf(
            ConversationDetails.Group(
                groupConversation,
                lastMessage = null,
                isSelfUserMember = true,
                isSelfUserCreator = true,
                unreadEventCount = emptyMap(),
                selfRole = Conversation.Member.Role.Member
            )
        )

        val firstOneOnOneDetails = ConversationDetails.OneOne(
            oneOnOneConversation,
            TestUser.OTHER,
            UserType.INTERNAL,
            lastMessage = null,
            unreadEventCount = emptyMap()
        )
        val secondOneOnOneDetails = ConversationDetails.OneOne(
            oneOnOneConversation,
            TestUser.OTHER.copy(name = "New User Name"),
            UserType.INTERNAL,
            lastMessage = null,
            unreadEventCount = emptyMap()
        )

        val oneOnOneDetailsChannel = Channel<ConversationDetails.OneOne>(Channel.UNLIMITED)

        val (_, observeConversationsUseCase) = Arrangement()
            .withConversationsList(conversations)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, groupConversationUpdates)
            .withConversationsDetailsChannelUpdates(oneOnOneConversation, oneOnOneDetailsChannel)
            .arrange()

        // When, Then
        observeConversationsUseCase(fetchArchivedConversations).test {
            oneOnOneDetailsChannel.send(firstOneOnOneDetails)

            val conversationList = awaitItem()
            assertContentEquals(groupConversationUpdates + firstOneOnOneDetails, conversationList)

            oneOnOneDetailsChannel.send(secondOneOnOneDetails)
            val updatedConversationList = awaitItem()
            assertContentEquals(groupConversationUpdates + secondOneOnOneDetails, updatedConversationList)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAConversationIsAddedToTheList_whenObservingDetailsList_thenTheUpdateIsPropagatedThroughTheFlow() = runTest {
        // Given
        val groupConversation = TestConversation.GROUP()
        val fetchArchivedConversations = false
        val groupConversationDetails = ConversationDetails.Group(
            groupConversation,
            lastMessage = null,
            isSelfUserMember = true,
            isSelfUserCreator = true,
            unreadEventCount = emptyMap(),
            selfRole = Conversation.Member.Role.Member
        )

        val selfConversation = TestConversation.SELF()
        val selfConversationDetails = ConversationDetails.Self(selfConversation)

        val firstConversationsList = listOf(groupConversation)
        val conversationListUpdates = Channel<List<Conversation>>(Channel.UNLIMITED)
        conversationListUpdates.send(firstConversationsList)

        val (_, observeConversationsUseCase) = Arrangement()
            .withConversationsList(conversationListUpdates)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .withSuccessfulConversationsDetailsListUpdates(selfConversation, listOf(selfConversationDetails))
            .arrange()

        // When, Then
        observeConversationsUseCase(fetchArchivedConversations).test {
            assertContentEquals(listOf(groupConversationDetails), awaitItem())

            conversationListUpdates.close()
            awaitComplete()
        }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAnOngoingCall_whenFetchingConversationDetails_thenTheConversationShouldHaveAnOngoingCall() = runTest {
        // Given
        val groupConversation = TestConversation.GROUP()
        val fetchArchivedConversations = false
        val groupConversationDetails = ConversationDetails.Group(
            groupConversation,
            lastMessage = null,
            isSelfUserMember = true,
            isSelfUserCreator = true,
            unreadEventCount = emptyMap(),
            selfRole = Conversation.Member.Role.Member
        )

        val firstConversationsList = listOf(groupConversation)

        val conversationListUpdates = Channel<List<Conversation>>(Channel.UNLIMITED)
        conversationListUpdates.send(firstConversationsList)

        val (_, observeConversationsUseCase) = Arrangement()
            .withConversationsList(conversationListUpdates)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .arrange()

        // When, Then
        observeConversationsUseCase(fetchArchivedConversations).test {
            assertEquals(true, (awaitItem()[0] as ConversationDetails.Group).hasOngoingCall)
        }
    }

    @Test
    fun givenAConversationWithoutAnOngoingCall_whenFetchingConversationDetails_thenTheConversationShouldNotHaveAnOngoingCall() = runTest {
        // Given
        val groupConversation = TestConversation.GROUP()
        val fetchArchivedConversations = false

        val groupConversationDetails = ConversationDetails.Group(
            groupConversation,
            lastMessage = null,
            isSelfUserMember = true,
            isSelfUserCreator = true,
            unreadEventCount = emptyMap(),
            selfRole = Conversation.Member.Role.Member
        )

        val firstConversationsList = listOf(groupConversation)

        val conversationListUpdates = Channel<List<Conversation>>(Channel.UNLIMITED)
        conversationListUpdates.send(firstConversationsList)

        val (_, observeConversationsUseCase) = Arrangement()
            .withConversationsList(conversationListUpdates)
            .withSuccessfulConversationsDetailsListUpdates(groupConversation, listOf(groupConversationDetails))
            .arrange()

        // When, Then
        observeConversationsUseCase(fetchArchivedConversations).test {
            assertEquals(false, (awaitItem()[0] as ConversationDetails.Group).hasOngoingCall)
        }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenConversationDetailsFailure_whenObservingDetailsList_thenIgnoreConversationWithFailure() = runTest {
        // Given
        val successConversation = TestConversation.ONE_ON_ONE().copy(id = ConversationId("successId", "domain"))
        val successConversationDetails = TestConversationDetails.CONVERSATION_ONE_ONE.copy(conversation = successConversation)
        val failureConversation = TestConversation.ONE_ON_ONE().copy(id = ConversationId("failedId", "domain"))
        val fetchArchivedConversations = false

        val (_, observeConversationsUseCase) = Arrangement()
            .withConversationsList(listOf(successConversation, failureConversation))
            .withSuccessfulConversationsDetailsListUpdates(successConversation, listOf(successConversationDetails))
            .withErrorConversationsDetailsListUpdates(failureConversation)
            .arrange()

        // When, Then
        observeConversationsUseCase(fetchArchivedConversations).test {
            assertEquals(awaitItem(), listOf(successConversationDetails))
            awaitComplete()
        }
    }

    private class Arrangement {

        @Mock
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        suspend fun withConversationsDetailsChannelUpdates(
            conversation: Conversation,
            expectedConversationDetails: Channel<ConversationDetails.OneOne>
        ) = apply {
            coEvery {
                conversationRepository.observeConversationDetailsById(eq(conversation.id))
            }.returns(expectedConversationDetails.consumeAsFlow().map { Either.Right(it) })
        }

        suspend fun withSuccessfulConversationsDetailsListUpdates(
            conversation: Conversation,
            expectedConversationDetailsList: List<ConversationDetails>
        ) = apply {
            coEvery {
                conversationRepository.observeConversationDetailsById(eq(conversation.id))
            }.returns(expectedConversationDetailsList.asFlow().map { Either.Right(it) })
        }

        suspend fun withErrorConversationsDetailsListUpdates(conversation: Conversation) = apply {
            coEvery {
                conversationRepository.observeConversationDetailsById(eq(conversation.id))
            }.returns(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        suspend fun withConversationsList(conversations: List<Conversation>) = apply {
            coEvery {
                conversationRepository.observeConversationList()
            }.returns(flowOf(conversations))
        }

        suspend fun withConversationsList(conversations: Channel<List<Conversation>>) = apply {
            coEvery {
                conversationRepository.observeConversationList()
            }.returns(conversations.consumeAsFlow())
        }

        fun arrange() = this to ObserveConversationListDetailsUseCaseImpl(conversationRepository)
    }

}
