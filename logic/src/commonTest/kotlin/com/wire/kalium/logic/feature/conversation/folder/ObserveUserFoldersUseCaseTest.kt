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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveUserFoldersUseCaseTest {

    @Test
    fun givenFoldersExist_WhenObserved_ThenReturnUserFolders() = runTest {
        val userFolders = listOf(
            ConversationFolder("1", "Custom Folder", FolderType.USER),
            ConversationFolder("2", "Second Folder", FolderType.USER)
        )
        val allFolders = userFolders + ConversationFolder("3", "", FolderType.FAVORITE)

        val (arrangement, observeUserFoldersUseCase) = Arrangement()
            .withObserveFolders(flowOf(Either.Right(allFolders)))
            .arrange()

        val result = observeUserFoldersUseCase().first()

        assertEquals(userFolders, result)

        coVerify {
            arrangement.conversationFolderRepository.observeFolders()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.fetchConversationFolders()
        }.wasNotInvoked()
    }

    @Test
    fun givenNoFoldersExist_WhenObserved_ThenFetchFoldersAndReturnEmptyList() = runTest {
        val (arrangement, observeUserFoldersUseCase) = Arrangement()
            .withObserveFolders(flowOf(Either.Right(emptyList())))
            .withFetchConversationFolders(Either.Right(Unit))
            .arrange()

        val result = observeUserFoldersUseCase().first()

        assertEquals(emptyList(), result)

        coVerify {
            arrangement.conversationFolderRepository.observeFolders()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.fetchConversationFolders()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenOnlyFavoriteFoldersExist_WhenObserved_ThenDoNotFetchFoldersAndReturnEmptyList() = runTest {
        val favoriteFolders = listOf(
            ConversationFolder("1", "Favorites", FolderType.FAVORITE),
        )

        val (arrangement, observeUserFoldersUseCase) = Arrangement()
            .withObserveFolders(flowOf(Either.Right(favoriteFolders)))
            .arrange()

        val result = observeUserFoldersUseCase().first()

        assertEquals(emptyList(), result)

        coVerify {
            arrangement.conversationFolderRepository.observeFolders()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.fetchConversationFolders()
        }.wasNotInvoked()
    }

    @Test
    fun givenErrorInObserveFolders_WhenObserved_ThenReturnEmptyList() = runTest {
        val (arrangement, observeUserFoldersUseCase) = Arrangement()
            .withObserveFolders(flowOf(Either.Left(CoreFailure.Unknown(null))))
            .arrange()

        val result = observeUserFoldersUseCase().first()

        assertEquals(emptyList(), result)

        coVerify {
            arrangement.conversationFolderRepository.observeFolders()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationFolderRepository.fetchConversationFolders()
        }.wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val conversationFolderRepository = mock(ConversationFolderRepository::class)

        private val observeUserFoldersUseCase = ObserveUserFoldersUseCaseImpl(
            conversationFolderRepository
        )

        suspend fun withObserveFolders(flow: Flow<Either<CoreFailure, List<ConversationFolder>>>) = apply {
            coEvery {
                conversationFolderRepository.observeFolders()
            }.returns(flow)
        }

        suspend fun withFetchConversationFolders(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationFolderRepository.fetchConversationFolders()
            }.returns(either)
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to observeUserFoldersUseCase }
    }
}
