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
package com.wire.kalium.logic.feature.selfDeletingMessages

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.selfdeletingMessages.ConversationSelfDeletionStatus
import com.wire.kalium.logic.feature.selfdeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.selfdeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl
import com.wire.kalium.logic.feature.selfdeletingMessages.SelfDeletionTimer
import com.wire.kalium.logic.feature.selfdeletingMessages.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ObserveSelfDeletingMessagesUseCaseTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenErrorWhenFetchingTeamSettings_whenObservingSelfDeletingStatus_thenFinalTimerMatchesTheStoredConversationOne() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val conversationDuration = 3600.toDuration(DurationUnit.SECONDS)
        val storedConversationStatus = ConversationSelfDeletionStatus(
            conversationId = conversationId,
            selfDeletionTimer = SelfDeletionTimer.Enabled(conversationDuration)
        )
        val storedTeamSettingsFlow = flowOf(Either.Left(StorageFailure.Generic(RuntimeException("DB failed"))))
        val storedConversationStatusFlow = flowOf(Either.Right(storedConversationStatus))

        val expectedSelfDeletionStatus =
            ConversationSelfDeletionStatus(conversationId, SelfDeletionTimer.Enabled(conversationDuration))

        val (arrangement, observeSelfDeletionMessagesFlag) = Arrangement()
            .withObserveTeamSettingsSelfDeletionStatus(storedTeamSettingsFlow)
            .withObserveConversationSelfDeletionStatus(storedConversationStatusFlow, conversationId)
            .arrange()

        val result = observeSelfDeletionMessagesFlag(conversationId, true)

        verify(arrangement.userConfigRepository).invocation { observeTeamSettingsSelfDeletingStatus() }
            .wasInvoked(exactly = once)
        verify(arrangement.userConfigRepository).invocation { observeConversationSelfDeletionTimer(conversationId) }
            .wasInvoked(exactly = once)
        assertEquals(expectedSelfDeletionStatus.selfDeletionTimer, result.first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenTeamSettingsEnabledButNotEnforcedValue_whenObserving_thenFinalTimerMatchesTheStoredConversationOne() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val storedTeamSettingsSelfDeletionStatus = TeamSettingsSelfDeletionStatus(
            hasFeatureChanged = null,
            enforcedSelfDeletionTimer = SelfDeletionTimer.Enabled(ZERO)
        )
        val conversationDuration = 3600.toDuration(DurationUnit.SECONDS)
        val storedConversationStatus = ConversationSelfDeletionStatus(
            conversationId = conversationId,
            selfDeletionTimer = SelfDeletionTimer.Enabled(conversationDuration)
        )
        val storedTeamSettingsFlow = flowOf(Either.Right(storedTeamSettingsSelfDeletionStatus))
        val storedConversationStatusFlow = flowOf(Either.Right(storedConversationStatus))

        val expectedSelfDeletionStatus = ConversationSelfDeletionStatus(conversationId, SelfDeletionTimer.Enabled(conversationDuration))

        val (arrangement, observeSelfDeletionMessagesFlag) = Arrangement()
            .withObserveTeamSettingsSelfDeletionStatus(storedTeamSettingsFlow)
            .withObserveConversationSelfDeletionStatus(storedConversationStatusFlow, conversationId)
            .arrange()

        val result = observeSelfDeletionMessagesFlag(conversationId, true)

        verify(arrangement.userConfigRepository).invocation { observeTeamSettingsSelfDeletingStatus() }
            .wasInvoked(exactly = once)
        verify(arrangement.userConfigRepository).invocation { observeConversationSelfDeletionTimer(conversationId) }
            .wasInvoked(exactly = once)
        assertEquals(expectedSelfDeletionStatus.selfDeletionTimer, result.first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenEnforcedStoredValueForTeamSettingsAndConversation_whenObserving_thenFinalTimerMatchesTheStoredTeamSettingsOne() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val storedTeamSettingsDuration = 7.toDuration(DurationUnit.DAYS)
        val storedTeamSettingsSelfDeletionStatus = TeamSettingsSelfDeletionStatus(
            hasFeatureChanged = null,
            enforcedSelfDeletionTimer = SelfDeletionTimer.Enforced.ByTeam(storedTeamSettingsDuration)
        )
        val conversationDuration = 1.toDuration(DurationUnit.HOURS)
        val storedConversationStatus = ConversationSelfDeletionStatus(
            conversationId = conversationId,
            selfDeletionTimer = SelfDeletionTimer.Enabled(conversationDuration)
        )
        val storedTeamSettingsFlow = flowOf(Either.Right(storedTeamSettingsSelfDeletionStatus))
        val storedConversationStatusFlow = flowOf(Either.Right(storedConversationStatus))


        val (arrangement, observeSelfDeletionTimer) = Arrangement()
            .withObserveTeamSettingsSelfDeletionStatus(storedTeamSettingsFlow)
            .withObserveConversationSelfDeletionStatus(storedConversationStatusFlow, conversationId)
            .arrange()

        val result = observeSelfDeletionTimer(conversationId, true)

        verify(arrangement.userConfigRepository).invocation { observeTeamSettingsSelfDeletingStatus() }
            .wasInvoked(exactly = once)
        verify(arrangement.userConfigRepository).invocation { observeConversationSelfDeletionTimer(conversationId) }
            .wasInvoked(exactly = once)
        assertEquals(storedTeamSettingsDuration, result.first().toDuration())
    }

    private class Arrangement {
        @Mock
        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        val observeSelfDeletionStatus: ObserveSelfDeletionTimerSettingsForConversationUseCase by lazy {
            ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(userConfigRepository, conversationRepository)
        }

        init {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeById)
                .whenInvokedWith(any())
                .thenReturn(flowOf(Either.Right(TestConversation.CONVERSATION)))
        }

        fun withObserveTeamSettingsSelfDeletionStatus(eitherFlow: Flow<Either<StorageFailure, TeamSettingsSelfDeletionStatus>>) = apply {
            given(userConfigRepository)
                .invocation { observeTeamSettingsSelfDeletingStatus() }
                .thenReturn(eitherFlow)
        }

        fun withObserveConversationSelfDeletionStatus(
            eitherFlow: Flow<Either<StorageFailure, ConversationSelfDeletionStatus>>,
            conversationId: ConversationId
        ) = apply {
            given(userConfigRepository)
                .invocation { userConfigRepository.observeConversationSelfDeletionTimer(conversationId) }
                .thenReturn(eitherFlow)
        }

        fun arrange() = this to observeSelfDeletionStatus
    }
}
