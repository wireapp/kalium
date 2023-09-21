/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateConversationArchivedStatusUseCaseTest {
    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    private lateinit var updateConversationArchivedStatus: UpdateConversationArchivedStatusUseCase

    @BeforeTest
    fun setup() {
        updateConversationArchivedStatus = UpdateConversationArchivedStatusUseCaseImpl(conversationRepository)
    }

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChange_thenShouldDelegateTheCallAndReturnASuccessResult() = runTest {
        val conversationId = TestConversation.ID
        val isConversationArchived = true
        val archivedStatusTimestamp = 123456789L

        given(conversationRepository)
            .suspendFunction(conversationRepository::updateArchivedStatusRemotely)
            .whenInvokedWith(any(), any(), any())
            .thenReturn(Either.Right(Unit))

        given(conversationRepository)
            .suspendFunction(conversationRepository::updateArchivedStatusLocally)
            .whenInvokedWith(any(), any(), any())
            .thenReturn(Either.Right(Unit))

        val result = updateConversationArchivedStatus(conversationId, isConversationArchived, archivedStatusTimestamp)
        assertEquals(ArchiveStatusUpdateResult.Success::class, result::class)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateArchivedStatusRemotely)
            .with(any(), eq(isConversationArchived), matching { it == archivedStatusTimestamp})
            .wasInvoked(exactly = once)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateArchivedStatusLocally)
            .with(any(), eq(isConversationArchived), matching { it == archivedStatusTimestamp})
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationId_whenInvokingAnArchivedStatusChangeAndFails_thenShouldDelegateTheCallAndReturnAFailureResult() = runTest {
        val conversationId = TestConversation.ID
        val isConversationArchived = true
        val archivedStatusTimestamp = 123456789L

        given(conversationRepository)
            .suspendFunction(conversationRepository::updateArchivedStatusRemotely)
            .whenInvokedWith(any(), any(), any())
            .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(RuntimeException("some error"))))

        val result = updateConversationArchivedStatus(conversationId, isConversationArchived, archivedStatusTimestamp)
        assertEquals(ArchiveStatusUpdateResult.Failure::class, result::class)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateArchivedStatusRemotely)
            .with(any(), eq(isConversationArchived), matching { it == archivedStatusTimestamp})
            .wasInvoked(exactly = once)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateArchivedStatusLocally)
            .with(any(), eq(isConversationArchived), matching { it == archivedStatusTimestamp})
            .wasNotInvoked()

    }
}
