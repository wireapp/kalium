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
package com.wire.kalium.logic.data.conversation.folders

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.FolderWithConversations
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.properties.LabelListResponseDTO
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderDAO
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderEntity
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderTypeEntity
import com.wire.kalium.persistence.dao.unread.ConversationUnreadEventEntity
import io.ktor.util.reflect.instanceOf
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationFolderRepositoryTest {

    @Test
    fun givenFavoriteFolderExistsWhenFetchingFavoriteFolderThenShouldReturnFolderSuccessfully() = runTest {
        // given
        val folder = ConversationFolderEntity(id = "folder1", name = "Favorites", type = ConversationFolderTypeEntity.FAVORITE)
        val arrangement = Arrangement().withFavoriteConversationFolder(folder)

        // when
        val result = arrangement.repository.getFavoriteConversationFolder()

        // then
        result.shouldSucceed {
            assertEquals(folder.toModel(), it)
        }
        coVerify { arrangement.conversationFolderDAO.getFavoriteConversationFolder() }.wasInvoked()
    }

    @Test
    fun givenConversationsInFolderWhenObservingConversationsFromFolderThenShouldEmitConversationsList() = runTest {
        // given
        val folderId = "folder1"
        val conversation = ConversationDetailsWithEventsEntity(
            conversationViewEntity = TestConversation.VIEW_ENTITY,
            lastMessage = null,
            messageDraft = null,
            unreadEvents = ConversationUnreadEventEntity(TestConversation.VIEW_ENTITY.id, mapOf()),
        )

        val conversations = listOf(conversation)
        val arrangement = Arrangement().withConversationsFromFolder(folderId, conversations)

        // when
        val resultFlow = arrangement.repository.observeConversationsFromFolder(folderId)

        // then
        val emittedConversations = resultFlow.first()
        assertEquals(arrangement.conversationMapper.fromDaoModelToDetailsWithEvents(conversations.first()), emittedConversations.first())
    }

    @Test
    fun givenFolderDataWhenUpdatingConversationFoldersThenFoldersShouldBeUpdatedInDatabaseSuccessfully() = runTest {
        // given
        val folders = listOf(
            FolderWithConversations(
                id = "folder1", name = "Favorites", type = FolderType.FAVORITE,
                conversationIdList = listOf()
            )
        )
        val arrangement = Arrangement().withSuccessfulFolderUpdate()

        // when
        val result = arrangement.repository.updateConversationFolders(folders)

        // then
        result.shouldSucceed()
        coVerify { arrangement.conversationFolderDAO.updateConversationFolders(any()) }.wasInvoked()
    }

    @Test
    fun givenNetworkFailureWhenFetchingConversationFoldersThenShouldReturnNetworkFailure() = runTest {
        // given
        val arrangement = Arrangement().withFetchConversationLabels(NetworkResponse.Error(KaliumException.NoNetwork()))

        // when
        val result = arrangement.repository.fetchConversationFolders()

        // then
        result.shouldFail { failure ->
            failure.instanceOf(NetworkFailure.NoNetworkConnection::class)
        }
    }

    private class Arrangement {

        @Mock
        val conversationFolderDAO = mock(ConversationFolderDAO::class)

        @Mock
        val userPropertiesApi = mock(PropertiesApi::class)

        private val selfUserId = TestUser.SELF.id

        val conversationMapper = MapperProvider.conversationMapper(selfUserId)

        val repository = ConversationFolderDataSource(
            conversationFolderDAO = conversationFolderDAO,
            userPropertiesApi = userPropertiesApi,
            selfUserId = selfUserId
        )

        suspend fun withFavoriteConversationFolder(folder: ConversationFolderEntity): Arrangement {
            coEvery { conversationFolderDAO.getFavoriteConversationFolder() }.returns(folder)
            return this
        }

        suspend fun withConversationsFromFolder(folderId: String, conversations: List<ConversationDetailsWithEventsEntity>): Arrangement {
            coEvery { conversationFolderDAO.observeConversationListFromFolder(folderId) }.returns(flowOf(conversations))
            return this
        }

        suspend fun withSuccessfulFolderUpdate(): Arrangement {
            coEvery { conversationFolderDAO.updateConversationFolders(any()) }.returns(Unit)
            return this
        }

        suspend fun withFetchConversationLabels(response: NetworkResponse<LabelListResponseDTO>): Arrangement {
            coEvery { userPropertiesApi.getLabels() }.returns(response)
            return this
        }
    }
}
