/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationFilter
import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.folder.GetFavoriteFolderUseCase
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObserveConversationListDetailsWithEventsUseCaseImplTest {

    @Test
    fun givenOngoingGroupCall_whenObservingAllConversations_thenGroupIsMarkedAndMovedToTop() = runTest {
        val ongoingGroupId = ConversationId("ongoing-group", "domain")
        val otherGroupId = ConversationId("other-group", "domain")
        val oneOnOneId = ConversationId("one-on-one", "domain")
        val conversations = listOf(
            oneOnOneConversation(oneOnOneId),
            groupConversation(otherGroupId),
            groupConversation(ongoingGroupId)
        )
        val (_, useCase) = Arrangement()
            .withOngoingCalls(ongoingGroupId)
            .withConversationList(fromArchive = false, conversations = conversations)
            .arrange()

        val result = useCase(false, ConversationFilter.All).first()

        assertEquals(
            listOf(ongoingGroupId, oneOnOneId, otherGroupId),
            result.map { it.conversationDetails.conversation.id }
        )
        assertTrue(result[0].hasOngoingCall)
        assertTrue(result[0].hasNewActivitiesToShow)
        assertFalse(result[2].hasOngoingCall)
    }

    @Test
    fun givenOngoingOneOnOneCall_whenObservingAllConversations_thenItIsNotMarkedOrMoved() = runTest {
        val oneOnOneId = ConversationId("one-on-one", "domain")
        val groupId = ConversationId("group", "domain")
        val conversations = listOf(
            oneOnOneConversation(oneOnOneId),
            groupConversation(groupId)
        )
        val (_, useCase) = Arrangement()
            .withOngoingCalls(oneOnOneId)
            .withConversationList(fromArchive = false, conversations = conversations)
            .arrange()

        val result = useCase(false, ConversationFilter.All).first()

        assertEquals(conversations, result)
        assertFalse(result[1].hasOngoingCall)
        assertFalse(result[0].hasNewActivitiesToShow)
    }

    @Test
    fun givenArchivedOngoingGroupCall_whenObservingArchivedConversations_thenGroupIsMarkedWithoutReordering() =
        runTest {
            val ongoingGroupId = ConversationId("ongoing-group", "domain")
            val oneOnOneId = ConversationId("one-on-one", "domain")
            val conversations = listOf(
                oneOnOneConversation(oneOnOneId),
                groupConversation(ongoingGroupId)
            )
            val (_, useCase) = Arrangement()
                .withOngoingCalls(ongoingGroupId)
                .withConversationList(fromArchive = true, conversations = conversations)
                .arrange()

            val result = useCase(true, ConversationFilter.All).first()

            assertEquals(
                listOf(oneOnOneId, ongoingGroupId),
                result.map { it.conversationDetails.conversation.id }
            )
            assertTrue(result[1].hasOngoingCall)
            assertTrue(result[1].hasNewActivitiesToShow)
        }

    @Test
    fun givenOngoingGroupCallInFolder_whenObservingFolder_thenGroupIsMarkedAndMovedToTop() = runTest {
        val folderId = "folder-id"
        val ongoingGroupId = ConversationId("ongoing-group", "domain")
        val oneOnOneId = ConversationId("one-on-one", "domain")
        val conversations = listOf(
            oneOnOneConversation(oneOnOneId),
            groupConversation(ongoingGroupId)
        )
        val (_, useCase) = Arrangement()
            .withOngoingCalls(ongoingGroupId)
            .withConversationsFromFolder(folderId, conversations)
            .arrange()

        val result = useCase(false, ConversationFilter.Folder("folder", folderId)).first()

        assertEquals(
            listOf(ongoingGroupId, oneOnOneId),
            result.map { it.conversationDetails.conversation.id }
        )
        assertTrue(result[0].hasOngoingCall)
    }

    @Test
    fun givenOngoingGroupCallInFavorites_whenObservingFavorites_thenGroupIsMarkedAndMovedToTop() = runTest {
        val favoriteFolder = ConversationFolder("favorite-id", "Favorites", FolderType.FAVORITE)
        val ongoingGroupId = ConversationId("ongoing-group", "domain")
        val oneOnOneId = ConversationId("one-on-one", "domain")
        val conversations = listOf(
            oneOnOneConversation(oneOnOneId),
            groupConversation(ongoingGroupId)
        )
        val (_, useCase) = Arrangement()
            .withOngoingCalls(ongoingGroupId)
            .withFavoriteFolder(favoriteFolder)
            .withConversationsFromFolder(favoriteFolder.id, conversations)
            .arrange()

        val result = useCase(false, ConversationFilter.Favorites).first()

        assertEquals(
            listOf(ongoingGroupId, oneOnOneId),
            result.map { it.conversationDetails.conversation.id }
        )
        assertTrue(result[0].hasOngoingCall)
    }

    private fun groupConversation(conversationId: ConversationId): ConversationDetailsWithEvents =
        ConversationDetailsWithEvents(
            TestConversationDetails.CONVERSATION_GROUP.copy(
                conversation = TestConversation.GROUP().copy(id = conversationId)
            )
        )

    private fun oneOnOneConversation(conversationId: ConversationId): ConversationDetailsWithEvents =
        ConversationDetailsWithEvents(
            TestConversationDetails.CONVERSATION_ONE_ONE.copy(
                conversation = TestConversation.ONE_ON_ONE().copy(id = conversationId)
            )
        )

    private class Arrangement {
        val conversationRepository = mock<ConversationRepository>()
        val conversationFolderRepository = mock<ConversationFolderRepository>(mode = MockMode.autoUnit)
        val getFavoriteFolder = mock<GetFavoriteFolderUseCase>()
        val callRepository = mock<CallRepository>()

        init {
            every {
                callRepository.joinableCallsFlow()
            } returns flowOf(emptyList())
        }

        fun withOngoingCalls(vararg conversationIds: ConversationId) = apply {
            every {
                callRepository.joinableCallsFlow()
            } returns flowOf(conversationIds.map { TestCall.groupIncomingCall(it) })
        }

        fun withConversationList(
            fromArchive: Boolean,
            conversations: List<ConversationDetailsWithEvents>,
            conversationFilter: ConversationFilter = ConversationFilter.All
        ) = apply {
            every {
                conversationRepository.observeConversationListDetailsWithEvents(
                    eq(fromArchive),
                    eq(conversationFilter),
                    eq(true)
                )
            } returns flowOf(conversations)
        }

        fun withConversationsFromFolder(
            folderId: String,
            conversations: List<ConversationDetailsWithEvents>
        ) = apply {
            every {
                conversationFolderRepository.observeConversationsFromFolder(eq(folderId))
            } returns flowOf(conversations)
        }

        suspend fun withFavoriteFolder(folder: ConversationFolder) = apply {
            everySuspend {
                getFavoriteFolder.invoke()
            } returns GetFavoriteFolderUseCase.Result.Success(folder)
        }

        fun arrange() = this to ObserveConversationListDetailsWithEventsUseCaseImpl(
            conversationRepository = conversationRepository,
            conversationFolderRepository = conversationFolderRepository,
            getFavoriteFolder = getFavoriteFolder,
            callRepository = callRepository
        )
    }
}
