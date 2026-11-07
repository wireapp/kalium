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
package com.wire.kalium.logic.feature.selfDeletingMessages

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class SelfDeletionSnapshotTest {
    @Test
    fun givenAbsentConversation_thenReturnsFailureWithoutObserving() = runTest {
        val conversations = mock<ConversationRepository>()
        val config = mock<UserConfigRepository>()
        everySuspend { conversations.getConversationById(TestConversation.ID) } returns Either.Left(StorageFailure.DataNotFound)
        val useCase = ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(config, conversations, testKaliumDispatcher)

        assertEquals(
            ObserveSelfDeletionTimerSettingsForConversationUseCase.Snapshot.Failure(StorageFailure.DataNotFound),
            useCase.getSnapshot(TestConversation.ID, true)
        )
        verifySuspend(VerifyMode.not) { conversations.observeConversationById(any()) }
    }

    @Test
    fun givenStoredConversation_thenPreservesTimerPrioritiesWithoutObserving() = runTest {
        val conversations = mock<ConversationRepository>()
        val config = mock<UserConfigRepository>()
        val conversation = TestConversation.CONVERSATION.copy(messageTimer = 30.seconds, userMessageTimer = 10.seconds)
        everySuspend { conversations.getConversationById(TestConversation.ID) } returns Either.Right(conversation)
        everySuspend { conversations.isCellEnabled(TestConversation.ID) } returns Either.Right(false)
        val useCase = ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(config, conversations, testKaliumDispatcher)
        for ((team, expected) in listOf(
            TeamSelfDeleteTimer.Disabled to SelfDeletionTimer.Disabled,
            TeamSelfDeleteTimer.Enabled to SelfDeletionTimer.Enforced.ByGroup(30.seconds),
            TeamSelfDeleteTimer.Enforced(60.seconds) to SelfDeletionTimer.Enforced.ByTeam(60.seconds)
        )) {
            everySuspend { config.getTeamSettingsSelfDeletionStatus() } returns
                Either.Right(TeamSettingsSelfDeletionStatus(null, team))
            assertEquals(
                ObserveSelfDeletionTimerSettingsForConversationUseCase.Snapshot.Success(expected),
                useCase.getSnapshot(TestConversation.ID, true)
            )
        }
        everySuspend { config.getTeamSettingsSelfDeletionStatus() } returns Either.Left(StorageFailure.DataNotFound)
        everySuspend { conversations.getConversationById(TestConversation.ID) } returns
            Either.Right(conversation.copy(messageTimer = null))
        assertEquals(
            ObserveSelfDeletionTimerSettingsForConversationUseCase.Snapshot.Success(SelfDeletionTimer.Enabled(10.seconds)),
            useCase.getSnapshot(TestConversation.ID, true)
        )
        assertEquals(
            ObserveSelfDeletionTimerSettingsForConversationUseCase.Snapshot.Success(SelfDeletionTimer.Enabled(null)),
            useCase.getSnapshot(TestConversation.ID, false)
        )
        verifySuspend(VerifyMode.not) { conversations.observeConversationById(any()) }
    }

    @Test
    fun givenCellsConversation_thenTimerIsDisabledWithoutReadingTeamSettings() = runTest {
        val conversations = mock<ConversationRepository>()
        val config = mock<UserConfigRepository>()
        everySuspend { conversations.getConversationById(TestConversation.ID) } returns Either.Right(TestConversation.CONVERSATION)
        everySuspend { conversations.isCellEnabled(TestConversation.ID) } returns Either.Right(true)
        val useCase = ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(config, conversations, testKaliumDispatcher)
        assertEquals(
            ObserveSelfDeletionTimerSettingsForConversationUseCase.Snapshot.Success(SelfDeletionTimer.Disabled),
            useCase.getSnapshot(TestConversation.ID, true)
        )
        verifySuspend(VerifyMode.not) { config.getTeamSettingsSelfDeletionStatus() }
    }

    @Test
    fun givenDeletionAfterSnapshot_thenNoObserverWaitIsIntroduced() = runTest {
        val conversations = mock<ConversationRepository>()
        val config = mock<UserConfigRepository>()
        var deleted = false
        everySuspend { conversations.getConversationById(TestConversation.ID) } calls {
            if (deleted) Either.Left(StorageFailure.DataNotFound) else Either.Right(TestConversation.CONVERSATION)
        }
        everySuspend { conversations.isCellEnabled(TestConversation.ID) } calls {
            deleted = true
            Either.Right(false)
        }
        everySuspend { config.getTeamSettingsSelfDeletionStatus() } returns Either.Left(StorageFailure.DataNotFound)
        val useCase = ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(config, conversations, testKaliumDispatcher)
        useCase.getSnapshot(TestConversation.ID, true)
        assertEquals(
            ObserveSelfDeletionTimerSettingsForConversationUseCase.Snapshot.Failure(StorageFailure.DataNotFound),
            useCase.getSnapshot(TestConversation.ID, true)
        )
        verifySuspend(VerifyMode.not) { conversations.observeConversationById(any()) }
    }
}
