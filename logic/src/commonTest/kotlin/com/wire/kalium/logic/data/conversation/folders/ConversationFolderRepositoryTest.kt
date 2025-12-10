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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.FolderWithConversations
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.properties.LabelListResponseDTO
import com.wire.kalium.network.api.authenticated.properties.PropertyKey
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderDAO
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderEntity
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderTypeEntity
import com.wire.kalium.persistence.dao.unread.ConversationUnreadEventEntity
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.http.HttpStatusCode
import io.ktor.util.reflect.instanceOf
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
        verifySuspend { arrangement.conversationFolderDAO.getFavoriteConversationFolder() }
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
        verifySuspend { arrangement.conversationFolderDAO.updateConversationFolders(any()) }
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

    @Test
    fun given404ErrorWhenFetchingFoldersThenShouldCreateEmptyLabelList() = runTest {
        // given
        val arrangement = Arrangement()
            .withSetProperty(NetworkResponse.Success(Unit, emptyMap(), HttpStatusCode.OK.value))
            .withSuccessfulFolderUpdate()
            .withFetchConversationLabels(
                NetworkResponse.Error(
                    KaliumException.InvalidRequestError(
                        errorResponse = ErrorResponse(
                            code = HttpStatusCode.NotFound.value,
                            message = "",
                            label = ""
                        )
                    )
                )
            )

        // when
        val result = arrangement.repository.fetchConversationFolders()

        // then
        result.shouldSucceed()
        verifySuspend {
            arrangement.userPropertiesApi.setProperty(
                PropertyKey.WIRE_LABELS,
                any()
            )
        }

        verifySuspend { arrangement.conversationFolderDAO.updateConversationFolders(any()) }
    }

    @Test
    fun givenValidConversationAndFolderWhenAddingConversationThenShouldAddSuccessfully() = runTest {
        // given
        val folderId = "folder1"
        val conversationId = TestConversation.ID
        val arrangement = Arrangement()
            .withAddConversationToFolder()
            .withGetFoldersWithConversations()
            .withUpdateLabels(NetworkResponse.Success(Unit, mapOf(), 200))

        // when
        val result = arrangement.repository.addConversationToFolder(conversationId, folderId)

        // then
        result.shouldSucceed()
        verifySuspend { arrangement.conversationFolderDAO.addConversationToFolder(conversationId.toDao(), folderId) }
    }

    @Test
    fun givenValidConversationAndFolderWhenRemovingConversationThenShouldRemoveSuccessfully() = runTest {
        // given
        val folderId = "folder1"
        val conversationId = TestConversation.ID
        val arrangement = Arrangement()
            .withRemoveConversationFromFolder()
            .withGetFoldersWithConversations()
            .withSuccessfulFolderRemoval()
            .withConversationsFromFolder(folderId, listOf())
            .withUpdateLabels(NetworkResponse.Success(Unit, mapOf(), 200))

        // when
        val result = arrangement.repository.removeConversationFromFolder(conversationId, folderId)

        // then
        result.shouldSucceed()
        verifySuspend { arrangement.conversationFolderDAO.removeConversationFromFolder(conversationId.toDao(), folderId) }
    }

    @Test
    fun givenLocalFoldersWhenSyncingFoldersThenShouldUpdateSuccessfully() = runTest {
        // given
        val folders = listOf(
            FolderWithConversations(
                id = "folder1",
                name = "Favorites",
                type = FolderType.FAVORITE,
                conversationIdList = emptyList()
            )
        )
        val arrangement = Arrangement()
            .withGetFoldersWithConversations(folders)
            .withUpdateLabels(NetworkResponse.Success(Unit, mapOf(), 200))

        // when
        val result = arrangement.repository.syncConversationFoldersFromLocal()

        // then
        result.shouldSucceed()
        verifySuspend { arrangement.userPropertiesApi.updateLabels(any()) }
        verifySuspend { arrangement.conversationFolderDAO.getFoldersWithConversations() }
    }

    @Test
    fun givenValidFolderIdWhenRemovingFolderThenShouldRemoveSuccessfully() = runTest {
        // given
        val folderId = "folder1"
        val arrangement = Arrangement().withSuccessfulFolderRemoval()

        // when
        val result = arrangement.repository.removeFolder(folderId)

        // then
        result.shouldSucceed()
        verifySuspend { arrangement.conversationFolderDAO.removeFolder(folderId) }
    }

    @Test
    fun givenValidFolderWhenAddingFolderThenShouldAddSuccessfully() = runTest {
        // given
        val folder = ConversationFolder(id = "folder1", name = "New Folder", type = FolderType.USER)
        val arrangement = Arrangement().withSuccessfulFolderAddition()

        // when
        val result = arrangement.repository.addFolder(folder)

        // then
        result.shouldSucceed()
        verifySuspend { arrangement.conversationFolderDAO.addFolder(folder.toDao()) }
    }

    @Test
    fun givenLastConversationRemovedFromFolder_whenRemovingConversation_thenFolderShouldBeDeleted() = runTest {
        // given
        val folderId = "folder1"
        val conversationId = TestConversation.ID

        val arrangement = Arrangement()
            .withRemoveConversationFromFolder()
            .withSuccessfulFolderRemoval()
            .withConversationsFromFolder(folderId, emptyList())

        // when
        val result = arrangement.repository.removeConversationFromFolder(conversationId, folderId)

        // then
        result.shouldSucceed()

        verifySuspend { arrangement.conversationFolderDAO.removeConversationFromFolder(conversationId.toDao(), folderId) }
        verifySuspend { arrangement.conversationFolderDAO.removeFolder(folderId) }
    }

    @Test
    fun givenLastConversationRemovedFromFavorite_whenRemovingConversation_thenFavoriteShouldNotBeDeleted() = runTest {
        // given
        val folderId = "folder1"
        val conversationId = TestConversation.ID

        val arrangement = Arrangement()
            .withRemoveConversationFromFolder()
            .withSuccessfulFolderRemoval()
            .withConversationsFromFolder(folderId, emptyList())

        // when
        val result = arrangement.repository.removeConversationFromFolder(conversationId, folderId, true)

        // then
        result.shouldSucceed()

        verifySuspend { arrangement.conversationFolderDAO.removeConversationFromFolder(conversationId.toDao(), folderId) }
        verifySuspend(VerifyMode.not) { arrangement.conversationFolderDAO.removeFolder(folderId) }
    }

    @Test
    fun givenRemainingConversationsInFolder_whenRemovingConversation_thenFolderShouldNotBeDeleted() = runTest {
        // given
        val folderId = "folder1"
        val conversationId = TestConversation.ID
        val remainingConversation = ConversationDetailsWithEventsEntity(
            conversationViewEntity = TestConversation.VIEW_ENTITY,
            lastMessage = null,
            messageDraft = null,
            unreadEvents = ConversationUnreadEventEntity(TestConversation.VIEW_ENTITY.id, mapOf())
        )

        val arrangement = Arrangement()
            .withRemoveConversationFromFolder()
            .withSuccessfulFolderRemoval()
            .withConversationsFromFolder(folderId, listOf(remainingConversation))

        // when
        val result = arrangement.repository.removeConversationFromFolder(conversationId, folderId)

        // then
        result.shouldSucceed()

        verifySuspend { arrangement.conversationFolderDAO.removeConversationFromFolder(conversationId.toDao(), folderId) }
        verifySuspend(VerifyMode.not) { arrangement.conversationFolderDAO.removeFolder(folderId) }
    }


    private class Arrangement {

        val conversationFolderDAO = mock<ConversationFolderDAO>()
        val userPropertiesApi = mock<PropertiesApi>()

        private val selfUserId = TestUser.SELF.id

        val conversationMapper = MapperProvider.conversationMapper(selfUserId)

        val repository = ConversationFolderDataSource(
            conversationFolderDAO = conversationFolderDAO,
            userPropertiesApi = userPropertiesApi,
            selfUserId = selfUserId
        )

        fun withFavoriteConversationFolder(folder: ConversationFolderEntity) = apply {
            everySuspend { conversationFolderDAO.getFavoriteConversationFolder() } returns folder
        }

        fun withConversationsFromFolder(folderId: String, conversations: List<ConversationDetailsWithEventsEntity>) = apply {
            everySuspend { conversationFolderDAO.observeConversationListFromFolder(folderId) } returns flowOf(conversations)
        }

        fun withSuccessfulFolderUpdate() = apply {
            everySuspend { conversationFolderDAO.updateConversationFolders(any()) } returns Unit
        }

        fun withFetchConversationLabels(response: NetworkResponse<LabelListResponseDTO>) = apply {
            everySuspend { userPropertiesApi.getLabels() } returns response
        }

        fun withSetProperty(response: NetworkResponse<Unit>) = apply {
            everySuspend { userPropertiesApi.setProperty(any(), any()) } returns response
        }

        fun withUpdateLabels(response: NetworkResponse<Unit>) = apply {
            everySuspend { userPropertiesApi.updateLabels(any()) } returns response
        }

        fun withGetFoldersWithConversations(folders: List<FolderWithConversations> = emptyList()) = apply {
            everySuspend { conversationFolderDAO.getFoldersWithConversations() } returns folders.map { it.toDao() }
        }

        fun withAddConversationToFolder() = apply {
            everySuspend { conversationFolderDAO.addConversationToFolder(any(), any()) } returns Unit
        }

        fun withRemoveConversationFromFolder() = apply {
            everySuspend { conversationFolderDAO.removeConversationFromFolder(any(), any()) } returns Unit
        }

        fun withSuccessfulFolderRemoval() = apply {
            everySuspend { conversationFolderDAO.removeFolder(any()) } returns Unit
        }

        fun withSuccessfulFolderAddition() = apply {
            everySuspend { conversationFolderDAO.addFolder(any()) } returns Unit
        }
    }
}
