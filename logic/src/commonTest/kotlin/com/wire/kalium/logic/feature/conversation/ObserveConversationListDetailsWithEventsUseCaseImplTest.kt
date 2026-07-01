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

class ObserveConversationListDetailsWithEventsUseCaseImplTest {

    @Test
    fun givenJoinableGroupCall_whenObservingAllConversations_thenGroupIsMovedToTop() = runTest {
        val joinableGroupId = ConversationId("joinable-group", "domain")
        val otherGroupId = ConversationId("other-group", "domain")
        val oneOnOneId = ConversationId("one-on-one", "domain")
        val conversations = listOf(
            oneOnOneConversation(oneOnOneId),
            groupConversation(otherGroupId),
            groupConversation(joinableGroupId)
        )
        val (_, useCase) = Arrangement()
            .withJoinableCalls(joinableGroupId)
            .withConversationList(fromArchive = false, conversations = conversations)
            .arrange()

        val result = useCase(false, ConversationFilter.All).first()

        assertEquals(
            listOf(joinableGroupId, oneOnOneId, otherGroupId),
            result.map { it.conversationDetails.conversation.id }
        )
    }

    @Test
    fun givenJoinableOneOnOneCall_whenObservingAllConversations_thenItIsNotMoved() = runTest {
        val oneOnOneId = ConversationId("one-on-one", "domain")
        val groupId = ConversationId("group", "domain")
        val conversations = listOf(
            oneOnOneConversation(oneOnOneId),
            groupConversation(groupId)
        )
        val (_, useCase) = Arrangement()
            .withJoinableCalls(oneOnOneId)
            .withConversationList(fromArchive = false, conversations = conversations)
            .arrange()

        val result = useCase(false, ConversationFilter.All).first()

        assertEquals(conversations, result)
    }

    @Test
    fun givenArchivedJoinableGroupCall_whenObservingArchivedConversations_thenGroupIsNotReordered() =
        runTest {
            val joinableGroupId = ConversationId("joinable-group", "domain")
            val oneOnOneId = ConversationId("one-on-one", "domain")
            val conversations = listOf(
                oneOnOneConversation(oneOnOneId),
                groupConversation(joinableGroupId)
            )
            val (_, useCase) = Arrangement()
                .withJoinableCalls(joinableGroupId)
                .withConversationList(fromArchive = true, conversations = conversations)
                .arrange()

            val result = useCase(true, ConversationFilter.All).first()

            assertEquals(
                listOf(oneOnOneId, joinableGroupId),
                result.map { it.conversationDetails.conversation.id }
            )
        }

    @Test
    fun givenJoinableGroupCallInFolder_whenObservingFolder_thenGroupIsMovedToTop() = runTest {
        val folderId = "folder-id"
        val joinableGroupId = ConversationId("joinable-group", "domain")
        val oneOnOneId = ConversationId("one-on-one", "domain")
        val conversations = listOf(
            oneOnOneConversation(oneOnOneId),
            groupConversation(joinableGroupId)
        )
        val (_, useCase) = Arrangement()
            .withJoinableCalls(joinableGroupId)
            .withConversationsFromFolder(folderId, conversations)
            .arrange()

        val result = useCase(false, ConversationFilter.Folder("folder", folderId)).first()

        assertEquals(
            listOf(joinableGroupId, oneOnOneId),
            result.map { it.conversationDetails.conversation.id }
        )
    }

    @Test
    fun givenJoinableGroupCallInFavorites_whenObservingFavorites_thenGroupIsMovedToTop() = runTest {
        val favoriteFolder = ConversationFolder("favorite-id", "Favorites", FolderType.FAVORITE)
        val joinableGroupId = ConversationId("joinable-group", "domain")
        val oneOnOneId = ConversationId("one-on-one", "domain")
        val conversations = listOf(
            oneOnOneConversation(oneOnOneId),
            groupConversation(joinableGroupId)
        )
        val (_, useCase) = Arrangement()
            .withJoinableCalls(joinableGroupId)
            .withFavoriteFolder(favoriteFolder)
            .withConversationsFromFolder(favoriteFolder.id, conversations)
            .arrange()

        val result = useCase(false, ConversationFilter.Favorites).first()

        assertEquals(
            listOf(joinableGroupId, oneOnOneId),
            result.map { it.conversationDetails.conversation.id }
        )
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

        fun withJoinableCalls(vararg conversationIds: ConversationId) = apply {
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
