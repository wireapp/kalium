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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.feature.conversation.folder.GetFavoriteFolderUseCase.Result
import com.wire.kalium.logic.framework.TestFolder
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetFavoriteFolderUseCaseTest {

    @Test
    fun givenFavoriteFolderExists_WhenInvoked_ThenReturnSuccess() = runTest {
        val (arrangement, getFavoriteFolderUseCase) = Arrangement()
            .withFavoriteFolder(Either.Right(TestFolder.FAVORITE))
            .arrange()

        val result = getFavoriteFolderUseCase()

        assertIs<Result.Success>(result)
        assertIs<ConversationFolder>(result.folder)
        assertEquals(TestFolder.FAVORITE.id, result.folder.id)

        coVerify {
            arrangement.conversationFolderRepository.getFavoriteConversationFolder()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFavoriteFolderDoesNotExist_WhenInvoked_ThenReturnFailure() = runTest {
        val (arrangement, getFavoriteFolderUseCase) = Arrangement()
            .withFavoriteFolder(Either.Left(CoreFailure.Unknown(null)))
            .arrange()

        val result = getFavoriteFolderUseCase()

        assertIs<Result.Failure>(result)

        coVerify {
            arrangement.conversationFolderRepository.getFavoriteConversationFolder()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationFolderRepository = mock(ConversationFolderRepository::class)

        private val getFavoriteFolderUseCase = GetFavoriteFolderUseCaseImpl(
            conversationFolderRepository
        )

        suspend fun withFavoriteFolder(either: Either<CoreFailure, ConversationFolder>) = apply {
            coEvery {
                conversationFolderRepository.getFavoriteConversationFolder()
            }.returns(either)
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to getFavoriteFolderUseCase }
    }
}