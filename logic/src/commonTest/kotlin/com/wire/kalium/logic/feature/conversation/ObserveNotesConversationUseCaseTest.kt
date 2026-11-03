/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObserveNotesConversationUseCaseTest {

    @Test
    fun givenFeatureFlagIsDisabled_whenObserving_thenShouldEmitNullWithoutObservingAnything() = runTest {
        val arrangement = Arrangement().withNotesToSelfEnabled(false)

        arrangement.useCase(testKaliumDispatcher).test {
            assertNull(awaitItem())
            awaitComplete()
        }

        verify(VerifyMode.not) { arrangement.userPropertyRepository.observeShowNotesStatus() }
        verify(VerifyMode.not) { arrangement.conversationRepository.observeMLSSelfConversationId() }
    }

    @Test
    fun givenShowNotesIsDisabled_whenObserving_thenShouldEmitNull() = runTest {
        val arrangement = Arrangement()
            .withShowNotes(false)
            .withMlsSelfConversation(SELF_MLS_ESTABLISHED)

        arrangement.useCase(testKaliumDispatcher).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenShowNotesFailsToLoad_whenObserving_thenShouldEmitNull() = runTest {
        val arrangement = Arrangement()
            .withShowNotesFailure()
            .withMlsSelfConversation(SELF_MLS_ESTABLISHED)

        arrangement.useCase(testKaliumDispatcher).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenEverythingIsEnabledAndGroupIsEstablished_whenObserving_thenShouldEmitTheSelfConversation() = runTest {
        val arrangement = Arrangement()
            .withShowNotes(true)
            .withMlsSelfConversation(SELF_MLS_ESTABLISHED)

        arrangement.useCase(testKaliumDispatcher).test {
            assertEquals(SELF_MLS_ESTABLISHED, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenMlsSelfConversationDoesNotExistYet_whenObserving_thenShouldEmitNull() = runTest {
        val arrangement = Arrangement().withShowNotes(true).withNoMlsSelfConversation()

        arrangement.useCase(testKaliumDispatcher).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenMlsGroupIsNotEstablished_whenObserving_thenShouldEmitNull() = runTest {
        val arrangement = Arrangement()
            .withShowNotes(true)
            .withMlsSelfConversation(selfWithGroupState(Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN))

        arrangement.useCase(testKaliumDispatcher).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenSelfConversationIsNotMls_whenObserving_thenShouldEmitNull() = runTest {
        val arrangement = Arrangement()
            .withShowNotes(true)
            .withMlsSelfConversation(ConversationDetails.Self(TestConversation.SELF(Conversation.ProtocolInfo.Proteus)))

        arrangement.useCase(testKaliumDispatcher).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenMlsIsDisabled_whenObserving_thenShouldEmitNull() = runTest {
        val arrangement = Arrangement()
            .withShowNotes(true)
            .withMlsEnabled(false)
            .withMlsSelfConversation(SELF_MLS_ESTABLISHED)

        arrangement.useCase(testKaliumDispatcher).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenShowNotesChanges_whenObserving_thenShouldFollowTheChanges() = runTest {
        val showNotes = MutableStateFlow(false)
        val arrangement = Arrangement()
            .withShowNotesFlow(showNotes)
            .withMlsSelfConversation(SELF_MLS_ESTABLISHED)

        arrangement.useCase(testKaliumDispatcher).test {
            assertNull(awaitItem())
            showNotes.value = true
            assertEquals(SELF_MLS_ESTABLISHED, awaitItem())
            showNotes.value = false
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {
        val userPropertyRepository = mock<UserPropertyRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val isMLSEnabled = mock<IsMLSEnabledUseCase>()

        private var notesToSelfEnabled = true

        init {
            everySuspend { isMLSEnabled.invoke() } returns true
        }

        fun withNotesToSelfEnabled(enabled: Boolean) = apply { notesToSelfEnabled = enabled }

        fun withShowNotes(show: Boolean) = withShowNotesFlow(MutableStateFlow(show))

        fun withShowNotesFlow(flow: Flow<Boolean>) = apply {
            every { userPropertyRepository.observeShowNotesStatus() } returns flow.map { Either.Right(it) }
        }

        fun withShowNotesFailure() = apply {
            every { userPropertyRepository.observeShowNotesStatus() } returns flowOf(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withMlsEnabled(enabled: Boolean) = apply {
            everySuspend { isMLSEnabled.invoke() } returns enabled
        }

        suspend fun withMlsSelfConversation(details: ConversationDetails.Self) = apply {
            every { conversationRepository.observeMLSSelfConversationId() } returns flowOf(details.conversation.id)
            everySuspend { conversationRepository.observeConversationDetailsById(any<ConversationId>()) } returns
                    flowOf(Either.Right(details))
        }

        fun withNoMlsSelfConversation() = apply {
            every { conversationRepository.observeMLSSelfConversationId() } returns flowOf(null)
        }

        fun useCase(dispatcher: KaliumDispatcher) = ObserveNotesConversationUseCaseImpl(
            notesToSelfEnabled = notesToSelfEnabled,
            userPropertyRepository = userPropertyRepository,
            conversationRepository = conversationRepository,
            isMLSEnabled = isMLSEnabled,
            dispatcher = dispatcher,
        )()
    }

    private companion object {
        val SELF_MLS_ESTABLISHED = selfWithGroupState(Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)

        fun selfWithGroupState(groupState: Conversation.ProtocolInfo.MLSCapable.GroupState) = ConversationDetails.Self(
            TestConversation.SELF(TestConversation.MLS_PROTOCOL_INFO.copy(groupState = groupState))
        )
    }
}
