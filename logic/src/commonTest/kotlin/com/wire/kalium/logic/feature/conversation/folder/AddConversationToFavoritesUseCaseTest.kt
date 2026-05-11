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
import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestFolder
import com.wire.kalium.common.functional.Either
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class AddConversationToFavoritesUseCaseTest {

    @Test
    fun givenValidConversation_WhenAddedToFavoritesSuccessfully_ThenReturnSuccess() = runTest {
        val (arrangement, addConversationToFavoritesUseCase) = Arrangement()
            .withFavoriteFolder(Either.Right(TestFolder.FAVORITE))
            .withAddConversationToFolder(Either.Right(Unit))
            .withSyncConversationFoldersFromLocal(Either.Right(Unit))
            .arrange()

        val result = addConversationToFavoritesUseCase(TestConversation.ID)

        assertIs<AddConversationToFavoritesUseCase.Result.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationFolderRepository.getFavoriteConversationFolder()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationFolderRepository.addConversationToFolder(
                eq(TestConversation.ID),
                eq(TestFolder.FAVORITE.id)
            )
        }
    }

    @Test
    fun givenValidConversation_WhenFavoriteFolderNotFound_ThenReturnFailure() = runTest {
        val (arrangement, addConversationToFavoritesUseCase) = Arrangement()
            .withFavoriteFolder(Either.Left(CoreFailure.Unknown(null)))
            .withSyncConversationFoldersFromLocal(Either.Right(Unit))
            .arrange()

        val result = addConversationToFavoritesUseCase(TestConversation.ID)

        assertIs<AddConversationToFavoritesUseCase.Result.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationFolderRepository.getFavoriteConversationFolder()
        }
    }

    @Test
    fun givenValidConversation_WhenAddToFolderFails_ThenReturnFailure() = runTest {
        val (arrangement, addConversationToFavoritesUseCase) = Arrangement()
            .withFavoriteFolder(Either.Right(TestFolder.FAVORITE))
            .withAddConversationToFolder(Either.Left(CoreFailure.Unknown(null)))
            .withSyncConversationFoldersFromLocal(Either.Right(Unit))
            .arrange()

        val result = addConversationToFavoritesUseCase(TestConversation.ID)

        assertIs<AddConversationToFavoritesUseCase.Result.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationFolderRepository.getFavoriteConversationFolder()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationFolderRepository.addConversationToFolder(
                eq(TestConversation.ID),
                eq(TestFolder.FAVORITE.id)
            )
        }
    }

    private class Arrangement {

        val conversationFolderRepository = mock<ConversationFolderRepository>(mode = MockMode.autoUnit)

        private val addConversationToFavoritesUseCase = AddConversationToFavoritesUseCaseImpl(
            conversationFolderRepository
        )

        suspend fun withFavoriteFolder(either: Either<CoreFailure, ConversationFolder>) = apply {
            everySuspend {
                conversationFolderRepository.getFavoriteConversationFolder()
            } returns either
        }

        suspend fun withAddConversationToFolder(either: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                conversationFolderRepository.addConversationToFolder(any(), any())
            } returns either
        }

        suspend fun withSyncConversationFoldersFromLocal(either: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                conversationFolderRepository.syncConversationFoldersFromLocal()
            } returns either
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to addConversationToFavoritesUseCase }
    }
}
