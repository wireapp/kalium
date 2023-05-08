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

package com.wire.kalium.persistence.config

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import com.wire.kalium.persistence.kmmSettings.KaliumPreferencesSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalCoroutinesApi::class)
class UserConfigStorageTest {
    private val settings: Settings = MapSettings()

    private val kaliumPreferences: KaliumPreferences = KaliumPreferencesSettings(settings)
    private lateinit var userConfigStorage: UserConfigStorage

    @BeforeTest
    fun setUp() {
        userConfigStorage = UserConfigStorageImpl(kaliumPreferences)
    }

    @AfterTest
    fun clear() {
        settings.clear()
    }

    @Test
    fun givenAFileSharingStatusValue_whenCAllPersistItSaveAnd_thenCanRestoreTheValueLocally() = runTest {
        userConfigStorage.persistFileSharingStatus(true, null)
        assertEquals(IsFileSharingEnabledEntity(true, null), userConfigStorage.isFileSharingEnabled())

        userConfigStorage.persistFileSharingStatus(false, null)
        assertEquals(IsFileSharingEnabledEntity(false, null), userConfigStorage.isFileSharingEnabled())
    }

    @Test
    fun givenAClassifiedDomainsStatusValue_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        userConfigStorage.persistClassifiedDomainsStatus(true, listOf("bella.com", "anta.wire"))
        assertEquals(
            ClassifiedDomainsEntity(true, listOf("bella.com", "anta.wire")),
            userConfigStorage.isClassifiedDomainsEnabledFlow().first()
        )
    }

    @Test
    fun givenAConferenceCallingStatusValue_whenPersistingIt_saveAndThenRestoreTheValueLocally() = runTest {
        userConfigStorage.persistConferenceCalling(true)
        assertEquals(
            true,
            userConfigStorage.isConferenceCallingEnabled()
        )
    }

    @Test
    fun givenAReadReceiptsSetValue_whenPersistingIt_saveAndThenRestoreTheValueLocally() = runTest {
        userConfigStorage.persistReadReceipts(true)
        assertTrue(userConfigStorage.isReadReceiptsEnabled().first())
    }

    @Test
    fun whenMarkingFileSharingAsNotified_thenIsChangedIsSetToFalse() = runTest {
        userConfigStorage.persistFileSharingStatus(true, true)
        userConfigStorage.setFileSharingAsNotified()
        assertEquals(IsFileSharingEnabledEntity(true, false), userConfigStorage.isFileSharingEnabled())
    }

    @Test
    fun givenPasswordChallengeRequirementIsNotSet_whenGettingItsValue_thenItShouldBeFalseByDefault() = runTest {
        assertFalse {
            userConfigStorage.isSecondFactorPasswordChallengeRequired()
        }
    }

    @Test
    fun givenPasswordChallengeRequirementIsSetToFalse_whenGettingItsValue_thenItShouldBeFalse() = runTest {
        userConfigStorage.persistSecondFactorPasswordChallengeStatus(false)
        assertFalse {
            userConfigStorage.isSecondFactorPasswordChallengeRequired()
        }
    }

    @Test
    fun givenPasswordChallengeRequirementIsSetToTrue_whenGettingItsValue_thenItShouldBeTrue() = runTest {
        userConfigStorage.persistSecondFactorPasswordChallengeStatus(true)
        assertTrue {
            userConfigStorage.isSecondFactorPasswordChallengeRequired()
        }
    }

    @Test
    fun givenGuestRoomLinkStatusIsSetToFalse_whenGettingItsValue_thenItShouldBeFalse() {
        userConfigStorage.persistGuestRoomLinkFeatureFlag(status = false, isStatusChanged = false)
        userConfigStorage.isGuestRoomLinkEnabled()?.status?.let {
            assertFalse { it }
        }
    }

    @Test
    fun givenGuestRoomLinkStatusIsSetToTrue_whenGettingItsValue_thenItShouldBeTrue() {
        userConfigStorage.persistGuestRoomLinkFeatureFlag(status = true, isStatusChanged = false)
        userConfigStorage.isGuestRoomLinkEnabled()?.status?.let {
            assertTrue { it }
        }
    }

    @Test
    fun givenValidEnabledTeamSettingsSelfDeletingStatus_whenGettingItsValue_thenItShouldBeRetrievedCorrectly() {
        val teamSettingsSelfDeletionStatusEntity = TeamSettingsSelfDeletionStatusEntity(
            selfDeletionTimerEntity = SelfDeletionTimerEntity.Enabled(ZERO),
            isStatusChanged = false
        )
        userConfigStorage.persistTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatusEntity)
        userConfigStorage.getTeamSettingsSelfDeletionStatus().let {
            assertTrue(it?.selfDeletionTimerEntity is SelfDeletionTimerEntity.Enabled)
            assertEquals(teamSettingsSelfDeletionStatusEntity.selfDeletionTimerEntity, it?.selfDeletionTimerEntity)
            assertTrue(it?.isStatusChanged == false)
        }
    }

    @Test
    fun givenValidEnforcedTeamSettingsSelfDeletingStatus_whenGettingItsValue_thenItShouldBeRetrievedCorrectly() {
        val enforcedTimeout = 1000.seconds
        val teamSettingsSelfDeletionStatusEntity = TeamSettingsSelfDeletionStatusEntity(
            selfDeletionTimerEntity = SelfDeletionTimerEntity.Enforced(enforcedTimeout),
            isStatusChanged = false
        )
        userConfigStorage.persistTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatusEntity)
        userConfigStorage.getTeamSettingsSelfDeletionStatus().let {
            assertTrue(it?.selfDeletionTimerEntity is SelfDeletionTimerEntity.Enforced)
            val retrievedDuration = (it?.selfDeletionTimerEntity as SelfDeletionTimerEntity.Enforced).enforcedDuration
            assertEquals(enforcedTimeout, retrievedDuration)
            assertTrue(it.isStatusChanged == false)
        }
    }

    @Test
    fun givenValidDisabledTeamSettingsSelfDeletingStatus_whenObservingItsValue_thenItShouldBeRetrievedCorrectly() = runTest {
        val teamSettingsSelfDeletionStatusEntity = TeamSettingsSelfDeletionStatusEntity(
            selfDeletionTimerEntity = SelfDeletionTimerEntity.Disabled,
            isStatusChanged = false
        )
        userConfigStorage.persistTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatusEntity)
        assertTrue(
            userConfigStorage.getTeamSettingsSelfDeletionStatusFlow().first()?.selfDeletionTimerEntity is SelfDeletionTimerEntity.Disabled
        )
        assertTrue(userConfigStorage.getTeamSettingsSelfDeletionStatusFlow().first()?.isStatusChanged == false)
    }

    @Test
    fun givenValidUserEnabledConversationSelfDeletingStatus_whenGettingItsValue_thenItShouldBeRetrievedCorrectly() = runTest {
        val conversationIDEntity = ConversationIDEntity("conversationID", "domain")
        val conversationUserDuration = 1.toDuration(DurationUnit.DAYS)
        val selfDeletionTimerEntity = SelfDeletionTimerEntity.Enabled(conversationUserDuration)
        userConfigStorage.persistConversationSelfDeletionTimer(conversationIDEntity, selfDeletionTimerEntity)
        assertTrue(userConfigStorage.getConversationSelfDeletionTimerFlow(conversationIDEntity).first() is SelfDeletionTimerEntity.Enabled)
        userConfigStorage.getConversationSelfDeletionTimerFlow(conversationIDEntity).first().let {
            assertEquals(selfDeletionTimerEntity, it)
        }
    }

    @Test
    fun givenValidUserEnabledConversationSelfDeletingStatus_whenObservingItsValue_thenItShouldBeRetrieveTheLastOneCorrectly() = runTest {
        val conversationIDEntity = ConversationIDEntity("conversationID", "domain")
        val conversationEnforcedTimer = 1.toDuration(DurationUnit.DAYS)
        val selfDeletionTimerEntity = SelfDeletionTimerEntity.Enforced(conversationEnforcedTimer)
        userConfigStorage.persistConversationSelfDeletionTimer(conversationIDEntity, selfDeletionTimerEntity)
        userConfigStorage.getConversationSelfDeletionTimerFlow(conversationIDEntity).first()?.let {
            assertTrue(it is SelfDeletionTimerEntity.Enforced && it.enforcedDuration == conversationEnforcedTimer)
        }
    }
}
