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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateConversationArchivedStatusUseCaseTest {

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChange_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        val conversationId = TestConversation.ID
        val isConversationArchived = true
        val archivedStatusTimestamp = 123456789L
        val onlyLocally = false

        val (arrangement, updateConversationArchivedStatus) = Arrangement()
            .withUpdateArchivedStatusFullSuccess()
            .arrange()

        val result = updateConversationArchivedStatus(conversationId, isConversationArchived, onlyLocally, archivedStatusTimestamp)
        assertEquals(ArchiveStatusUpdateResult.Success::class, result::class)

        with(arrangement) {
            coVerify {
                conversationRepository.updateArchivedStatusRemotely(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matches { it == archivedStatusTimestamp }
                )
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.updateArchivedStatusLocally(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matches { it == archivedStatusTimestamp }
                )
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChangeAndUserIsNotMember_thenShouldArchiveOnlyLocally() =
        runTest {
            val conversationId = TestConversation.ID
            val isConversationArchived = true
            val archivedStatusTimestamp = 123456789L
            val onlyLocally = true

            val (arrangement, updateConversationArchivedStatus) = Arrangement()
                .withUpdateArchivedStatusFullSuccess()
                .arrange()

            val result = updateConversationArchivedStatus(conversationId, isConversationArchived, onlyLocally, archivedStatusTimestamp)
            assertEquals(ArchiveStatusUpdateResult.Success::class, result::class)

            with(arrangement) {
                coVerify {
                    conversationRepository.updateArchivedStatusRemotely(any(), any(), any())
                }.wasNotInvoked()

                coVerify {
                    conversationRepository.updateArchivedStatusLocally(
                        conversationId = any(),
                        isArchived = eq(isConversationArchived),
                        archivedStatusTimestamp = matches { it == archivedStatusTimestamp }
                    )
                }.wasInvoked(exactly = once)
            }
        }

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChangeAndFails_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val conversationId = TestConversation.ID
        val isConversationArchived = true
        val archivedStatusTimestamp = 123456789L
        val onlyLocally = false

        val (arrangement, updateConversationArchivedStatus) = Arrangement()
            .withRemoteUpdateArchivedStatusFailure()
            .arrange()

        val result = updateConversationArchivedStatus(conversationId, isConversationArchived, onlyLocally, archivedStatusTimestamp)
        assertEquals(ArchiveStatusUpdateResult.Failure::class, result::class)

        with(arrangement) {
            coVerify {
                conversationRepository.updateArchivedStatusRemotely(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matches { it == archivedStatusTimestamp }
                )
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.updateArchivedStatusLocally(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matches { it == archivedStatusTimestamp }
                )
            }.wasNotInvoked()
        }
    }

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChangeAndFailsOnlyLocally_thenShouldReturnAFailureResult() = runTest {
        val conversationId = TestConversation.ID
        val isConversationArchived = true
        val archivedStatusTimestamp = 123456789L
        val onlyLocally = false

        val (arrangement, updateConversationArchivedStatus) = Arrangement()
            .withLocalUpdateArchivedStatusFailure()
            .arrange()

        val result = updateConversationArchivedStatus(conversationId, isConversationArchived, onlyLocally, archivedStatusTimestamp)
        assertEquals(ArchiveStatusUpdateResult.Failure::class, result::class)

        with(arrangement) {
            coVerify {
                conversationRepository.updateArchivedStatusRemotely(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matches { it == archivedStatusTimestamp }
                )
            }.wasInvoked(exactly = once)

            coVerify {
                conversationRepository.updateArchivedStatusLocally(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matches { it == archivedStatusTimestamp }
                )
            }.wasInvoked(exactly = once)
        }
    }

    private class Arrangement {
        @Mock
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        private val updateArchivedStatus = UpdateConversationArchivedStatusUseCaseImpl(conversationRepository)

        suspend fun withUpdateArchivedStatusFullSuccess() = apply {
            coEvery {
                conversationRepository.updateArchivedStatusRemotely(any(), any(), any())
            }.returns(Either.Right(Unit))

            coEvery {
                conversationRepository.updateArchivedStatusLocally(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withRemoteUpdateArchivedStatusFailure() = apply {
            coEvery {
                conversationRepository.updateArchivedStatusRemotely(any(), any(), any())
            }.returns(Either.Left(NetworkFailure.ServerMiscommunication(RuntimeException("some error"))))
        }

        suspend fun withLocalUpdateArchivedStatusFailure() = apply {
            coEvery {
                conversationRepository.updateArchivedStatusRemotely(any(), any(), any())
            }.returns(Either.Right(Unit))
            coEvery {
                conversationRepository.updateArchivedStatusLocally(any(), any(), any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        fun arrange() = this to updateArchivedStatus
    }
}
