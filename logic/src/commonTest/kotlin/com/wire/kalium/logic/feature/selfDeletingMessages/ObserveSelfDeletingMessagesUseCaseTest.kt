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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.ConversationSelfDeletionStatus
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ObserveSelfDeletingMessagesUseCaseTest {

    @Test
    fun givenErrorWhenFetchingTeamSettings_whenObservingSelfDeletingStatus_thenFinalTimerMatchesTheStoredConversationOne() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val conversationDuration = 3600.toDuration(DurationUnit.SECONDS)
        val storedConversationStatus = TEST_CONVERSION.copy(
            messageTimer = conversationDuration
        )
        val storedTeamSettingsFlow = flowOf(Either.Left(StorageFailure.Generic(RuntimeException("DB failed"))))

        val (arrangement, observeSelfDeletionMessagesFlag) = Arrangement(testKaliumDispatcher)
            .withObserveTeamSettingsSelfDeletionStatus(storedTeamSettingsFlow)
            .withStoredConversation(storedConversationStatus)
            .withCellEnabled(false)
            .arrange()

        val result = observeSelfDeletionMessagesFlag(conversationId, true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.observeTeamSettingsSelfDeletingStatus()
        }
        assertEquals(storedConversationStatus.messageTimer, result.first().duration)
    }

    @Test
    fun givenTeamSettingsEnabledButNotEnforcedValue_whenObserving_thenFinalTimerMatchesTheStoredGroupConversationOne() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val conversationSettingsDuration = 3600.toDuration(DurationUnit.SECONDS)
        val userStoredConversationDuration = 10.toDuration(DurationUnit.SECONDS)

        val storedTeamSettingsSelfDeletionStatus = TeamSettingsSelfDeletionStatus(
            hasFeatureChanged = null,
            enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Enabled
        )
        val userStoredConversationStatus = TEST_CONVERSION.copy(
            messageTimer = conversationSettingsDuration,
            userMessageTimer = userStoredConversationDuration
        )
        val storedTeamSettingsFlow = flowOf(Either.Right(storedTeamSettingsSelfDeletionStatus))

        val expectedSelfDeletionStatus =
            ConversationSelfDeletionStatus(conversationId, SelfDeletionTimer.Enforced.ByGroup(conversationSettingsDuration))

        val (arrangement, observeSelfDeletionMessagesFlag) = Arrangement(testKaliumDispatcher)
            .withObserveTeamSettingsSelfDeletionStatus(storedTeamSettingsFlow)
            .withStoredConversation(userStoredConversationStatus)
            .withCellEnabled(false)
            .arrange()

        val result = observeSelfDeletionMessagesFlag(conversationId, true)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                userConfigRepository.observeTeamSettingsSelfDeletingStatus()
            }

            assertEquals(expectedSelfDeletionStatus.selfDeletionTimer, result.first())
        }
    }

    @Test
    fun givenEnforcedStoredValueForTeamSettingsAndConversation_whenObserving_thenFinalTimerMatchesTheStoredTeamSettingsOne() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val storedTeamSettingsDuration = 7.toDuration(DurationUnit.DAYS)
        val storedTeamSettingsSelfDeletionStatus = TeamSettingsSelfDeletionStatus(
            hasFeatureChanged = null,
            enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Enforced(storedTeamSettingsDuration)
        )
        val conversationDuration = 1.toDuration(DurationUnit.HOURS)
        val storedConversationStatus = TEST_CONVERSION.copy(
            messageTimer = conversationDuration
        )
        val storedTeamSettingsFlow = flowOf(Either.Right(storedTeamSettingsSelfDeletionStatus))

        val (arrangement, observeSelfDeletionTimer) = Arrangement(testKaliumDispatcher)
            .withObserveTeamSettingsSelfDeletionStatus(storedTeamSettingsFlow)
            .withStoredConversation(storedConversationStatus)
            .withCellEnabled(false)
            .arrange()

        val result = observeSelfDeletionTimer(conversationId, true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.observeTeamSettingsSelfDeletingStatus()
        }

        assertEquals(storedTeamSettingsDuration, result.first().duration)
    }

    @Test
    fun givenNoEnforcedTeamAndConversationSelfDeletionSetting_whenObserving_thenFinalTimerMatchesTheUserStoredOne() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val storedTeamSettingsSelfDeletionStatus = TeamSettingsSelfDeletionStatus(
            hasFeatureChanged = null,
            enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Enabled
        )
        val conversationDuration = 1.toDuration(DurationUnit.HOURS)
        val storedConversationStatus = TEST_CONVERSION.copy(
            messageTimer = conversationDuration
        )
        val storedTeamSettingsFlow = flowOf(Either.Right(storedTeamSettingsSelfDeletionStatus))

        val (arrangement, observeSelfDeletionTimer) = Arrangement(testKaliumDispatcher)
            .withObserveTeamSettingsSelfDeletionStatus(storedTeamSettingsFlow)
            .withStoredConversation(storedConversationStatus)
            .withCellEnabled(false)
            .arrange()

        val result = observeSelfDeletionTimer(conversationId, true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.observeTeamSettingsSelfDeletingStatus()
        }

        assertEquals(conversationDuration, result.first().duration)
    }

    @Test
    fun givenNoEnforcedTeamConversationAndUserStoredSelfDeletionSetting_whenObserving_thenFinalTimerMatchesTheUserStoredOne() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val storedTeamSettingsSelfDeletionStatus = flowOf(
            Either.Right(
                TeamSettingsSelfDeletionStatus(
                    hasFeatureChanged = null,
                    enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Enabled
                )
            )
        )
        val storedConversationStatus = TEST_CONVERSION.copy(
            messageTimer = 1.toDuration(DurationUnit.HOURS),
            userMessageTimer = null
        )

        val (arrangement, observeSelfDeletionTimer) = Arrangement(testKaliumDispatcher)
            .withObserveTeamSettingsSelfDeletionStatus(storedTeamSettingsSelfDeletionStatus)
            .withStoredConversation(storedConversationStatus)
            .withCellEnabled(false)
            .arrange()

        val result = observeSelfDeletionTimer(conversationId, true)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.observeTeamSettingsSelfDeletingStatus()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.observeConversationById(any())
        }

        assertEquals(storedConversationStatus.messageTimer, result.first().duration)
    }

    @Test
    fun givenCellEnabledForConversation_whenObserving_thenDisabledStatusReturned() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val storedTeamSettingsSelfDeletionStatus = flowOf(
            Either.Right(
                TeamSettingsSelfDeletionStatus(
                    hasFeatureChanged = null,
                    enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Enabled
                )
            )
        )
        val storedConversationStatus = TEST_CONVERSION.copy(
            messageTimer = 1.toDuration(DurationUnit.HOURS),
            userMessageTimer = null
        )

        val (arrangement, observeSelfDeletionTimer) = Arrangement(testKaliumDispatcher)
            .withObserveTeamSettingsSelfDeletionStatus(storedTeamSettingsSelfDeletionStatus)
            .withStoredConversation(storedConversationStatus)
            .withCellEnabled(true)
            .arrange()

        val result = observeSelfDeletionTimer(conversationId, true)

        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.observeTeamSettingsSelfDeletingStatus()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.observeConversationById(any())
        }

        assertEquals(SelfDeletionTimer.Disabled, result.first())
    }

    private companion object {
        val TEST_CONVERSION = TestConversation.CONVERSATION
    }

    private class Arrangement(private var dispatcher: KaliumDispatcher = TestKaliumDispatcher) {
                val userConfigRepository: UserConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val conversationRepository: ConversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)

        val observeSelfDeletionStatus: ObserveSelfDeletionTimerSettingsForConversationUseCase by lazy {
            ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl(userConfigRepository, conversationRepository, dispatcher)
        }

        suspend fun withStoredConversation(conversation: Conversation) = apply {
            everySuspend {
                conversationRepository.observeConversationById(any())
            } returns flowOf(Either.Right(conversation))
        }

        suspend fun withObserveTeamSettingsSelfDeletionStatus(eitherFlow: Flow<Either<StorageFailure, TeamSettingsSelfDeletionStatus>>) =
            apply {
                everySuspend {
                    userConfigRepository.observeTeamSettingsSelfDeletingStatus()
                } returns eitherFlow
            }

        suspend fun withCellEnabled(enabled: Boolean) = apply {
            everySuspend {
                conversationRepository.isCellEnabled(any())
            } returns enabled.right()
        }

        fun arrange() = this to observeSelfDeletionStatus
    }
}
