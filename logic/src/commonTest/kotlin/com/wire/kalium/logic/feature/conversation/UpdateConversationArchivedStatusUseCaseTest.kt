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
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.common.functional.Either
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateConversationArchivedStatusUseCaseTest {

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChange_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        val conversationId = TestConversation.ID
        val isConversationArchived = true
        val archivedStatusTimestamp = Instant.fromEpochMilliseconds(123456789L)
        val onlyLocally = false

        val (arrangement, updateConversationArchivedStatus) = Arrangement()
            .withUpdateArchivedStatusFullSuccess()
            .arrange()

        val result = updateConversationArchivedStatus(conversationId, isConversationArchived, onlyLocally, archivedStatusTimestamp)
        assertEquals(ArchiveStatusUpdateResult.Success::class, result::class)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.updateArchivedStatusRemotely(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matching { it == archivedStatusTimestamp }
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.updateArchivedStatusLocally(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matching { it == archivedStatusTimestamp }
                )
            }
        }
    }

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChangeAndUserIsNotMember_thenShouldArchiveOnlyLocally() =
        runTest {
            val conversationId = TestConversation.ID
            val isConversationArchived = true
            val archivedStatusTimestamp = Instant.fromEpochMilliseconds(123456789L)
            val onlyLocally = true

            val (arrangement, updateConversationArchivedStatus) = Arrangement()
                .withUpdateArchivedStatusFullSuccess()
                .arrange()

            val result = updateConversationArchivedStatus(conversationId, isConversationArchived, onlyLocally, archivedStatusTimestamp)
            assertEquals(ArchiveStatusUpdateResult.Success::class, result::class)

            with(arrangement) {
                verifySuspend(VerifyMode.not) {
                    conversationRepository.updateArchivedStatusRemotely(any(), any(), any())
                }

                verifySuspend(VerifyMode.exactly(1)) {
                    conversationRepository.updateArchivedStatusLocally(
                        conversationId = any(),
                        isArchived = eq(isConversationArchived),
                        archivedStatusTimestamp = matching { it == archivedStatusTimestamp }
                    )
                }
            }
        }

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChangeAndFails_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val conversationId = TestConversation.ID
        val isConversationArchived = true
        val archivedStatusTimestamp = Instant.fromEpochMilliseconds(123456789L)
        val onlyLocally = false

        val (arrangement, updateConversationArchivedStatus) = Arrangement()
            .withRemoteUpdateArchivedStatusFailure()
            .arrange()

        val result = updateConversationArchivedStatus(conversationId, isConversationArchived, onlyLocally, archivedStatusTimestamp)
        assertEquals(ArchiveStatusUpdateResult.Failure::class, result::class)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.updateArchivedStatusRemotely(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matching { it == archivedStatusTimestamp }
                )
            }

            verifySuspend(VerifyMode.not) {
                conversationRepository.updateArchivedStatusLocally(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matching { it == archivedStatusTimestamp }
                )
            }
        }
    }

    @Test
    fun givenRemoteNoConversationError_whenInvokingAnArchivedStatusChange_thenShouldArchiveLocallyAndReturnSuccess() = runTest {
        val conversationId = TestConversation.ID
        val isConversationArchived = true
        val archivedStatusTimestamp = Instant.fromEpochMilliseconds(123456789L)
        val onlyLocally = false

        val (arrangement, updateConversationArchivedStatus) = Arrangement()
            .withRemoteNoConversationFailure()
            .arrange()

        val result = updateConversationArchivedStatus(conversationId, isConversationArchived, onlyLocally, archivedStatusTimestamp)
        assertEquals(ArchiveStatusUpdateResult.Success::class, result::class)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.updateArchivedStatusRemotely(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matching { it == archivedStatusTimestamp }
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.updateArchivedStatusLocally(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matching { it == archivedStatusTimestamp }
                )
            }
        }
    }

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChangeAndFailsOnlyLocally_thenShouldReturnAFailureResult() = runTest {
        val conversationId = TestConversation.ID
        val isConversationArchived = true
        val archivedStatusTimestamp = Instant.fromEpochMilliseconds(123456789L)
        val onlyLocally = false

        val (arrangement, updateConversationArchivedStatus) = Arrangement()
            .withLocalUpdateArchivedStatusFailure()
            .arrange()

        val result = updateConversationArchivedStatus(conversationId, isConversationArchived, onlyLocally, archivedStatusTimestamp)
        assertEquals(ArchiveStatusUpdateResult.Failure::class, result::class)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.updateArchivedStatusRemotely(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matching { it == archivedStatusTimestamp }
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.updateArchivedStatusLocally(
                    conversationId = any(),
                    isArchived = eq(isConversationArchived),
                    archivedStatusTimestamp = matching { it == archivedStatusTimestamp }
                )
            }
        }
    }

    private class Arrangement {

        val conversationRepository: ConversationRepository = mock()

        private val updateArchivedStatus = UpdateConversationArchivedStatusUseCaseImpl(conversationRepository)

        suspend fun withUpdateArchivedStatusFullSuccess() = apply {
            everySuspend {
                conversationRepository.updateArchivedStatusRemotely(any(), any(), any())
            } returns Either.Right(Unit)

            everySuspend {
                conversationRepository.updateArchivedStatusLocally(any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withRemoteUpdateArchivedStatusFailure() = apply {
            everySuspend {
                conversationRepository.updateArchivedStatusRemotely(any(), any(), any())
            } returns Either.Left(NetworkFailure.ServerMiscommunication(RuntimeException("some error")))
        }

        suspend fun withRemoteNoConversationFailure() = apply {
            everySuspend {
                conversationRepository.updateArchivedStatusRemotely(any(), any(), any())
            } returns Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.noConversation))

            everySuspend {
                conversationRepository.updateArchivedStatusLocally(any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withLocalUpdateArchivedStatusFailure() = apply {
            everySuspend {
                conversationRepository.updateArchivedStatusRemotely(any(), any(), any())
            } returns Either.Right(Unit)
            everySuspend {
                conversationRepository.updateArchivedStatusLocally(any(), any(), any())
            } returns Either.Left(StorageFailure.DataNotFound)
        }

        fun arrange() = this to updateArchivedStatus
    }
}
