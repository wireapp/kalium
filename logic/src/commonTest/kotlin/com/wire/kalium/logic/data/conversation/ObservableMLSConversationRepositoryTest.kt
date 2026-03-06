/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObservableMLSConversationRepositoryTest {

    @Test
    fun givenDelegateSucceeds_whenDecryptingMessage_thenHookIsNotified() = runTest {
        val arrangement = Arrangement().withDecryptMessageSuccess().arrange()

        arrangement.repository.decryptMessage(arrangement.context, byteArrayOf(), GROUP_ID)

        assertEquals(listOf(USER_ID), arrangement.hook.calls)
    }

    @Test
    fun givenDelegateFails_whenDecryptingMessage_thenHookIsNotNotified() = runTest {
        val arrangement = Arrangement().withDecryptMessageFailure().arrange()

        arrangement.repository.decryptMessage(arrangement.context, byteArrayOf(), GROUP_ID)

        assertEquals(emptyList(), arrangement.hook.calls)
    }

    @Test
    fun givenDelegateSucceeds_whenEstablishingGroup_thenHookIsNotified() = runTest {
        val arrangement = Arrangement().withEstablishSuccess().arrange()

        arrangement.repository.establishMLSGroup(arrangement.context, GROUP_ID, emptyList(), null, false)

        assertEquals(listOf(USER_ID), arrangement.hook.calls)
    }

    @Test
    fun givenDelegateFails_whenEstablishingGroup_thenHookNotNotified() = runTest {
        val arrangement = Arrangement().withEstablishFailure().arrange()

        arrangement.repository.establishMLSGroup(arrangement.context, GROUP_ID, emptyList(), null, false)

        assertEquals(emptyList(), arrangement.hook.calls)
    }

    @Test
    fun givenDelegateSucceeds_whenCommittingProposals_thenHookIsNotified() = runTest {
        val arrangement = Arrangement().withCommitSuccess().arrange()

        arrangement.repository.commitPendingProposals(arrangement.context, GROUP_ID)

        assertEquals(listOf(USER_ID), arrangement.hook.calls)
    }

    @Test
    fun givenDelegateFails_whenCommittingProposals_thenHookNotNotified() = runTest {
        val arrangement = Arrangement().withCommitFailure().arrange()

        arrangement.repository.commitPendingProposals(arrangement.context, GROUP_ID)

        assertEquals(emptyList(), arrangement.hook.calls)
    }

    @Test
    fun givenDelegateSucceeds_whenRotatingKeys_thenHookIsNotified() = runTest {
        val arrangement = Arrangement().withRotateSuccess().arrange()

        arrangement.repository.rotateKeysAndMigrateConversations(
            arrangement.context,
            ClientId("client"),
            arrangement.e2eiClient,
            "cert",
            listOf(GROUP_ID),
            false
        )

        assertEquals(listOf(USER_ID), arrangement.hook.calls)
    }

    @Test
    fun givenDelegateFails_whenRotatingKeys_thenHookNotNotified() = runTest {
        val arrangement = Arrangement().withRotateFailure().arrange()

        arrangement.repository.rotateKeysAndMigrateConversations(
            arrangement.context,
            ClientId("client"),
            arrangement.e2eiClient,
            "cert",
            listOf(GROUP_ID),
            false
        )

        assertEquals(emptyList(), arrangement.hook.calls)
    }

    private class Arrangement {
        private val delegate = mock(MLSConversationRepository::class)
        private val hook = RecordingHookNotifier()
        private val repository = ObservableMLSConversationRepository(delegate, USER_ID, hook)
        private val context = mock(MlsCoreCryptoContext::class)
        private val e2eiClient = mock(E2EIClient::class)

        suspend fun withEstablishSuccess() = apply {
            coEvery {
                delegate.establishMLSGroup(any(), any(), any(), any(), any())
            }.returns(Either.Right(MLSAdditionResult(emptySet(), emptySet())))
        }

        suspend fun withEstablishFailure() = apply {
            coEvery {
                delegate.establishMLSGroup(any(), any(), any(), any(), any())
            }.returns(Either.Left(CoreFailure.Unknown(null)))
        }

        suspend fun withCommitSuccess() = apply {
            coEvery { delegate.commitPendingProposals(any(), any()) }.returns(Either.Right(Unit))
        }

        suspend fun withCommitFailure() = apply {
            coEvery { delegate.commitPendingProposals(any(), any()) }.returns(Either.Left(CoreFailure.Unknown(null)))
        }

        suspend fun withRotateSuccess() = apply {
            coEvery {
                delegate.rotateKeysAndMigrateConversations(any(), any(), any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withRotateFailure() = apply {
            coEvery {
                delegate.rotateKeysAndMigrateConversations(any(), any(), any(), any(), any(), any())
            }.returns(Either.Left(E2EIFailure.Generic(Exception("boom"))))
        }

        suspend fun withDecryptMessageSuccess() = apply {
            coEvery {
                delegate.decryptMessage(any(), any(), any())
            }.returns(Either.Right(emptyList()))
        }

        suspend fun withDecryptMessageFailure() = apply {
            coEvery {
                delegate.decryptMessage(any(), any(), any())
            }.returns(CoreFailure.Unknown(null).left())
        }


        fun arrange(): ArrangementResult = ArrangementResult(repository, hook, context, e2eiClient)
    }

    private data class ArrangementResult(
        val repository: MLSConversationRepository,
        val hook: RecordingHookNotifier,
        val context: MlsCoreCryptoContext,
        val e2eiClient: E2EIClient,
    )

    private class RecordingHookNotifier : CryptoStateChangeHookNotifier {
        val calls = mutableListOf<UserId>()

        override suspend fun onCryptoStateChanged(userId: UserId) {
            calls += userId
        }
    }

    private companion object {
        val USER_ID = UserId("user", "domain")
        val GROUP_ID = GroupID("group-id")
    }
}
