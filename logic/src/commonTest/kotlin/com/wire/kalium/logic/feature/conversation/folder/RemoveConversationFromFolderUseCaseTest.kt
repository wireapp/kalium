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
package com.wire.kalium.logic.feature.conversation.folder

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.folder.RemoveConversationFromFolderUseCase.Result
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class RemoveConversationFromFolderUseCaseTest {

    @Test
    fun givenValidConversationAndFolder_WhenRemoveAndSyncSuccessful_ThenReturnSuccess() = runTest {
        val testConversationId = ConversationId("conversation-value", "conversation-domain")
        val testFolderId = "test-folder-id"

        val (arrangement, removeConversationUseCase) = Arrangement()
            .withRemoveConversationFromFolder(testConversationId, testFolderId, Either.Right(Unit))
            .withObserveConversationsFromFolder(testFolderId, flowOf(emptyList()))
            .withRemoveFolder(testFolderId, Either.Right(Unit))
            .withSyncFolders(Either.Right(Unit))
            .arrange()

        val result = removeConversationUseCase(testConversationId, testFolderId)

        assertIs<Result.Success>(result)

        coVerify {
            arrangement.conversationFolderRepository.removeConversationFromFolder(testConversationId, testFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.observeConversationsFromFolder(testFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.removeFolder(testFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.syncConversationFoldersFromLocal()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFolderNotEmpty_WhenRemoveAndSyncSuccessful_ThenReturnSuccessWithoutFolderRemoval() = runTest {
        val testConversationId = ConversationId("conversation-value", "conversation-domain")
        val testFolderId = "test-folder-id"

        val (arrangement, removeConversationUseCase) = Arrangement()
            .withRemoveConversationFromFolder(testConversationId, testFolderId, Either.Right(Unit))
            .withObserveConversationsFromFolder(
                testFolderId,
                flowOf(listOf(ConversationDetailsWithEvents(TestConversationDetails.CONVERSATION_GROUP)))
            )
            .withSyncFolders(Either.Right(Unit))
            .arrange()

        val result = removeConversationUseCase(testConversationId, testFolderId)

        assertIs<Result.Success>(result)

        coVerify {
            arrangement.conversationFolderRepository.removeConversationFromFolder(testConversationId, testFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.observeConversationsFromFolder(testFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.removeFolder(testFolderId)
        }.wasNotInvoked()

        coVerify {
            arrangement.conversationFolderRepository.syncConversationFoldersFromLocal()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenErrorDuringFolderRemoval_WhenObservedEmpty_ThenReturnFailure() = runTest {
        val testConversationId = ConversationId("conversation-value", "conversation-domain")
        val testFolderId = "test-folder-id"

        val (arrangement, removeConversationUseCase) = Arrangement()
            .withRemoveConversationFromFolder(testConversationId, testFolderId, Either.Right(Unit))
            .withObserveConversationsFromFolder(testFolderId, flowOf(emptyList()))
            .withRemoveFolder(testFolderId, Either.Left(CoreFailure.Unknown(null)))
            .arrange()

        val result = removeConversationUseCase(testConversationId, testFolderId)

        assertIs<Result.Failure>(result)

        coVerify {
            arrangement.conversationFolderRepository.removeConversationFromFolder(testConversationId, testFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.observeConversationsFromFolder(testFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.removeFolder(testFolderId)
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationFolderRepository = mock(ConversationFolderRepository::class)

        private val removeConversationFromFolderUseCase = RemoveConversationFromFolderUseCaseImpl(
            conversationFolderRepository
        )

        suspend fun withRemoveConversationFromFolder(
            conversationId: ConversationId,
            folderId: String,
            either: Either<CoreFailure, Unit>
        ) = apply {
            coEvery {
                conversationFolderRepository.removeConversationFromFolder(conversationId, folderId)
            }.returns(either)
        }

        suspend fun withObserveConversationsFromFolder(
            folderId: String,
            flow: Flow<List<ConversationDetailsWithEvents>>
        ) = apply {
            coEvery {
                conversationFolderRepository.observeConversationsFromFolder(folderId)
            }.returns(flow)
        }

        suspend fun withRemoveFolder(
            folderId: String,
            either: Either<CoreFailure, Unit>
        ) = apply {
            coEvery {
                conversationFolderRepository.removeFolder(folderId)
            }.returns(either)
        }

        suspend fun withSyncFolders(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationFolderRepository.syncConversationFoldersFromLocal()
            }.returns(either)
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to removeConversationFromFolderUseCase }
    }
}
