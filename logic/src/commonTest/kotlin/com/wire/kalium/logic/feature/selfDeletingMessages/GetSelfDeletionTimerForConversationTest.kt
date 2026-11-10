/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.selfDeletingMessages

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.framework.TestConversation
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class GetSelfDeletionTimerForConversationTest {
    @Test
    fun cellsStorageFailureDoesNotSilentlyDisableMessageExpiry() = runTest {
        val conversations = mock<ConversationRepository>()
        val config = mock<UserConfigRepository>()
        val failure = StorageFailure.Generic(IllegalStateException("storage unavailable"))
        everySuspend { conversations.getConversationById(any()) } returns Either.Right(TestConversation.ONE_ON_ONE())
        everySuspend { conversations.isCellEnabled(any()) } returns Either.Left(failure)

        assertEquals(Either.Left(failure), GetSelfDeletionTimerForConversationImpl(conversations, config)(TestConversation.ID))
    }

    @Test
    fun cellsDisableTheTimerWithoutReadingTeamPolicy() = runTest {
        val conversations = mock<ConversationRepository>()
        val config = mock<UserConfigRepository>()
        everySuspend { conversations.getConversationById(any()) } returns Either.Right(TestConversation.ONE_ON_ONE())
        everySuspend { conversations.isCellEnabled(any()) } returns Either.Right(true)

        assertEquals(Either.Right(SelfDeletionTimer.Disabled), GetSelfDeletionTimerForConversationImpl(conversations, config)(TestConversation.ID))
    }

    @Test
    fun teamGroupAndPersonalTimerPrecedenceIsPreserved() = runTest {
        val conversations = mock<ConversationRepository>()
        val config = mock<UserConfigRepository>()
        val snapshot = TestConversation.ONE_ON_ONE().copy(messageTimer = 60.seconds, userMessageTimer = 10.seconds)
        everySuspend { conversations.getConversationById(any()) } returns Either.Right(snapshot)
        everySuspend { conversations.isCellEnabled(any()) } returns Either.Right(false)
        val getTimer = GetSelfDeletionTimerForConversationImpl(conversations, config)

        for ((policy, expected) in listOf(
            TeamSelfDeleteTimer.Disabled to SelfDeletionTimer.Disabled,
            TeamSelfDeleteTimer.Enforced(90.seconds) to SelfDeletionTimer.Enforced.ByTeam(90.seconds),
            TeamSelfDeleteTimer.Enabled to SelfDeletionTimer.Enforced.ByGroup(60.seconds),
        )) {
            every { config.observeTeamSettingsSelfDeletingStatus() } returns
                flowOf(Either.Right(TeamSettingsSelfDeletionStatus(null, policy)))
            assertEquals(Either.Right(expected), getTimer(TestConversation.ID))
        }
        everySuspend { conversations.getConversationById(any()) } returns Either.Right(snapshot.copy(messageTimer = null))
        assertEquals(Either.Right(SelfDeletionTimer.Enabled(10.seconds)), getTimer(TestConversation.ID))
    }

    @Test
    fun missingConversationReturnsStorageFailureWithoutWaitingForAnObserver() = runTest {
        val conversations = mock<ConversationRepository>()
        val config = mock<UserConfigRepository>()
        everySuspend { conversations.getConversationById(TestConversation.ID) } returns Either.Left(StorageFailure.DataNotFound)
        everySuspend { conversations.observeConversationById(any()) } returns flow { kotlinx.coroutines.awaitCancellation() }

        assertEquals(Either.Left(StorageFailure.DataNotFound), withTimeout(1_000) {
            GetSelfDeletionTimerForConversationImpl(conversations, config)(TestConversation.ID)
        })
        verifySuspend(VerifyMode.not) { conversations.observeConversationById(any()) }
    }

    @Test
    fun deletionAfterSnapshotDoesNotWaitForConversationToReappear() = runTest {
        val conversations = mock<ConversationRepository>()
        val config = mock<UserConfigRepository>()
        everySuspend { conversations.isCellEnabled(any()) } returns Either.Right(false)
        val snapshot = TestConversation.ONE_ON_ONE().copy(messageTimer = 60.seconds, userMessageTimer = 10.seconds)
        everySuspend { conversations.getConversationById(TestConversation.ID) } returns Either.Right(snapshot)
        everySuspend { conversations.observeConversationById(any()) } returns flow { kotlinx.coroutines.awaitCancellation() }
        every { config.observeTeamSettingsSelfDeletingStatus() } returns flow {
            everySuspend { conversations.getConversationById(TestConversation.ID) } returns Either.Left(StorageFailure.DataNotFound)
            emit(Either.Right(TeamSettingsSelfDeletionStatus(null, TeamSelfDeleteTimer.Enabled)))
        }

        val getTimer = GetSelfDeletionTimerForConversationImpl(conversations, config)
        assertEquals(Either.Right(SelfDeletionTimer.Enforced.ByGroup(60.seconds)), withTimeout(1_000) {
            getTimer(TestConversation.ID)
        })
        assertEquals(Either.Left(StorageFailure.DataNotFound), getTimer(TestConversation.ID))
        verifySuspend(VerifyMode.not) { conversations.observeConversationById(any()) }
    }
}
