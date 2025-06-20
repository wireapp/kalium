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
package com.wire.kalium.logic.feature.conversation.folder

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.folder.MoveConversationToFolderUseCase.Result
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class MoveConversationToFolderUseCaseTest {

    @Test
    fun givenValidConversationAndFolder_WhenMoveIsSuccessful_ThenReturnSuccess() = runTest {
        val testConversationId = TestConversation.ID
        val testFolderId = "test-folder-id"
        val previousFolderId = "previous-folder-id"

        val (arrangement, moveConversationUseCase) = Arrangement()
            .withRemoveConversationFromFolder(testConversationId, previousFolderId, Either.Right(Unit))
            .withAddConversationToFolder(testConversationId, testFolderId, Either.Right(Unit))
            .withSyncFolders(Either.Right(Unit))
            .arrange()

        val result = moveConversationUseCase(testConversationId, testFolderId, previousFolderId)

        assertIs<Result.Success>(result)

        coVerify {
            arrangement.conversationFolderRepository.removeConversationFromFolder(testConversationId, previousFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.addConversationToFolder(testConversationId, testFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.syncConversationFoldersFromLocal()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidConversationAndFolder_WhenRemoveFails_ThenReturnFailure() = runTest {
        val testConversationId = TestConversation.ID
        val testFolderId = "test-folder-id"
        val previousFolderId = "previous-folder-id"

        val (arrangement, moveConversationUseCase) = Arrangement()
            .withRemoveConversationFromFolder(testConversationId, previousFolderId, Either.Left(CoreFailure.Unknown(null)))
            .arrange()

        val result = moveConversationUseCase(testConversationId, testFolderId, previousFolderId)

        assertIs<Result.Failure>(result)

        coVerify {
            arrangement.conversationFolderRepository.removeConversationFromFolder(testConversationId, previousFolderId)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidConversationAndFolder_WhenAddFails_ThenReturnFailure() = runTest {
        val testConversationId = TestConversation.ID
        val testFolderId = "test-folder-id"
        val previousFolderId = "previous-folder-id"

        val (arrangement, moveConversationUseCase) = Arrangement()
            .withRemoveConversationFromFolder(testConversationId, previousFolderId, Either.Right(Unit))
            .withAddConversationToFolder(testConversationId, testFolderId, Either.Left(CoreFailure.Unknown(null)))
            .arrange()

        val result = moveConversationUseCase(testConversationId, testFolderId, previousFolderId)

        assertIs<Result.Failure>(result)

        coVerify {
            arrangement.conversationFolderRepository.removeConversationFromFolder(testConversationId, previousFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.addConversationToFolder(testConversationId, testFolderId)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidConversationAndFolder_WhenSyncFails_ThenReturnFailure() = runTest {
        val testConversationId = TestConversation.ID
        val testFolderId = "test-folder-id"
        val previousFolderId = "previous-folder-id"

        val (arrangement, moveConversationUseCase) = Arrangement()
            .withRemoveConversationFromFolder(testConversationId, previousFolderId, Either.Right(Unit))
            .withAddConversationToFolder(testConversationId, testFolderId, Either.Right(Unit))
            .withSyncFolders(Either.Left(CoreFailure.Unknown(null)))
            .arrange()

        val result = moveConversationUseCase(testConversationId, testFolderId, previousFolderId)

        assertIs<Result.Failure>(result)

        coVerify {
            arrangement.conversationFolderRepository.removeConversationFromFolder(testConversationId, previousFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.addConversationToFolder(testConversationId, testFolderId)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.syncConversationFoldersFromLocal()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        val conversationFolderRepository = mock(ConversationFolderRepository::class)

        private val moveConversationToFolderUseCase = MoveConversationToFolderUseCaseImpl(
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

        suspend fun withAddConversationToFolder(
            conversationId: ConversationId,
            folderId: String,
            either: Either<CoreFailure, Unit>
        ) = apply {
            coEvery {
                conversationFolderRepository.addConversationToFolder(conversationId, folderId)
            }.returns(either)
        }

        suspend fun withSyncFolders(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationFolderRepository.syncConversationFoldersFromLocal()
            }.returns(either)
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to moveConversationToFolderUseCase }
    }
}

