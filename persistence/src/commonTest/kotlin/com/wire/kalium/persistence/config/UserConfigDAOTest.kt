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

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.config.UserConfigDAO
import com.wire.kalium.persistence.dao.config.model.AppLockConfigEntity
import com.wire.kalium.persistence.dao.config.model.ClassifiedDomainsEntity
import com.wire.kalium.persistence.dao.config.model.IsFileSharingEnabledEntity
import com.wire.kalium.persistence.dao.config.model.SelfDeletionTimerEntity
import com.wire.kalium.persistence.dao.config.model.TeamSettingsSelfDeletionStatusEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UserConfigDAOTest : BaseDatabaseTest() {

    private lateinit var userConfigDAO: UserConfigDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")
     init {
         Dispatchers.setMain(dispatcher)
     }

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, enableWAL = true)
        userConfigDAO = db.userConfigDAO
    }

    @Test
    fun givenValidEnabledTeamSettingsSelfDeletingStatus_whenGettingItsValue_thenItShouldBeRetrievedCorrectly() = runTest {
        val teamSettingsSelfDeletionStatusEntity = TeamSettingsSelfDeletionStatusEntity(
            selfDeletionTimerEntity = SelfDeletionTimerEntity.Enabled,
            isStatusChanged = false
        )
        userConfigDAO.setTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatusEntity)
        userConfigDAO.getTeamSettingsSelfDeletionStatus().let {
            assertIs<SelfDeletionTimerEntity.Enabled>(it?.selfDeletionTimerEntity)
            assertEquals(teamSettingsSelfDeletionStatusEntity.selfDeletionTimerEntity, it?.selfDeletionTimerEntity)
            assertFalse(it?.isStatusChanged!!)
        }
    }

    @Test
    fun givenValidEnforcedTeamSettingsSelfDeletingStatus_whenGettingItsValue_thenItShouldBeRetrievedCorrectly() = runTest {
        val enforcedTimeout = 1000.seconds
        val teamSettingsSelfDeletionStatusEntity = TeamSettingsSelfDeletionStatusEntity(
            selfDeletionTimerEntity = SelfDeletionTimerEntity.Enforced(enforcedTimeout),
            isStatusChanged = false
        )
        userConfigDAO.setTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatusEntity)
        userConfigDAO.getTeamSettingsSelfDeletionStatus().let {
            assertTrue(it?.selfDeletionTimerEntity is SelfDeletionTimerEntity.Enforced)
            val retrievedDuration = (it?.selfDeletionTimerEntity as SelfDeletionTimerEntity.Enforced).enforcedDuration
            assertEquals(enforcedTimeout, retrievedDuration)
            assertFalse(it.isStatusChanged!!)
        }
    }

    @Test
    fun givenValidDisabledTeamSettingsSelfDeletingStatus_whenObservingItsValue_thenItShouldBeRetrievedCorrectly() = runTest {
        val teamSettingsSelfDeletionStatusEntity = TeamSettingsSelfDeletionStatusEntity(
            selfDeletionTimerEntity = SelfDeletionTimerEntity.Disabled,
            isStatusChanged = false
        )
        userConfigDAO.setTeamSettingsSelfDeletionStatus(teamSettingsSelfDeletionStatusEntity)

        userConfigDAO.getTeamSettingsSelfDeletionStatus().also {
            assertIs<SelfDeletionTimerEntity.Disabled>(it?.selfDeletionTimerEntity)
            assertFalse(it?.isStatusChanged!!)
        }
    }

    @Test
    fun givenValidEnabledTeamSettingsSelfDeletingStatus_whenObservingItsValue_thenItShouldBeRetrievedCorrectly() = runTest {
        userConfigDAO.observeTeamSettingsSelfDeletingStatus().test {
            val firstNullValue = awaitItem()
            assertNull(firstNullValue)

            val expectedFirstValue = TeamSettingsSelfDeletionStatusEntity(
                selfDeletionTimerEntity = SelfDeletionTimerEntity.Enabled,
                isStatusChanged = false
            )

            userConfigDAO.setTeamSettingsSelfDeletionStatus(expectedFirstValue)
            val firstValue = awaitItem()
            assertEquals(expectedFirstValue, firstValue)

            val expectedSecondValue = TeamSettingsSelfDeletionStatusEntity(
                selfDeletionTimerEntity = SelfDeletionTimerEntity.Disabled,
                isStatusChanged = true
            )
            userConfigDAO.setTeamSettingsSelfDeletionStatus(expectedSecondValue)
            val secondValue = awaitItem()
            assertEquals(expectedSecondValue, secondValue)

            val thirdExpectedValue = expectedSecondValue.copy(isStatusChanged = false)
            userConfigDAO.setTeamSettingsSelfDeletionStatus(thirdExpectedValue)
            val thirdValue = awaitItem()
            assertEquals(thirdExpectedValue, thirdValue)
        }
    }

    @Test
    fun givenAFileSharingStatusValue_whenCAllPersistItSaveAnd_thenCanRestoreTheValueLocally() = runTest {
        userConfigDAO.persistFileSharingStatus(true, null)
        assertEquals(IsFileSharingEnabledEntity(true, null), userConfigDAO.isFileSharingEnabled())

        userConfigDAO.persistFileSharingStatus(false, null)
        assertEquals(IsFileSharingEnabledEntity(false, null), userConfigDAO.isFileSharingEnabled())
    }

    @Test
    fun givenAClassifiedDomainsStatusValue_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        userConfigDAO.persistClassifiedDomainsStatus(true, listOf("bella.com", "anta.wire"))
        assertEquals(
            ClassifiedDomainsEntity(true, listOf("bella.com", "anta.wire")),
            userConfigDAO.isClassifiedDomainsEnabledFlow().first()
        )
    }

    @Test
    fun givenAConferenceCallingStatusValue_whenPersistingIt_saveAndThenRestoreTheValueLocally() = runTest {
        userConfigDAO.persistConferenceCalling(true)
        assertEquals(
            true,
            userConfigDAO.isConferenceCallingEnabled()
        )
    }

    @Test
    fun givenAReadReceiptsSetValue_whenPersistingIt_saveAndThenRestoreTheValueLocally() = runTest {
        userConfigDAO.persistReadReceipts(true)
        assertTrue(userConfigDAO.areReadReceiptsEnabled().first())
    }

    @Test
    fun whenMarkingFileSharingAsNotified_thenIsChangedIsSetToFalse() = runTest {
        userConfigDAO.persistFileSharingStatus(true, true)
        userConfigDAO.setFileSharingAsNotified()
        assertEquals(IsFileSharingEnabledEntity(true, false), userConfigDAO.isFileSharingEnabled())
    }

    @Test
    fun givenPasswordChallengeRequirementIsNotSet_whenGettingItsValue_thenItShouldBeFalseByDefault() = runTest {
        assertFalse {
            userConfigDAO.isSecondFactorPasswordChallengeRequired()
        }
    }

    @Test
    fun givenPasswordChallengeRequirementIsSetToFalse_whenGettingItsValue_thenItShouldBeFalse() = runTest {
        userConfigDAO.persistSecondFactorPasswordChallengeStatus(false)
        assertFalse {
            userConfigDAO.isSecondFactorPasswordChallengeRequired()
        }
    }

    @Test
    fun givenPasswordChallengeRequirementIsSetToTrue_whenGettingItsValue_thenItShouldBeTrue() = runTest {
        userConfigDAO.persistSecondFactorPasswordChallengeStatus(true)
        assertTrue {
            userConfigDAO.isSecondFactorPasswordChallengeRequired()
        }
    }

    @Test
    fun givenGuestRoomLinkStatusIsSetToFalse_whenGettingItsValue_thenItShouldBeFalse() = runTest {
        userConfigDAO.persistGuestRoomLinkFeatureFlag(status = false, isStatusChanged = false)
        userConfigDAO.isGuestRoomLinkEnabled()?.status?.let {
            assertFalse { it }
        }
    }

    @Test
    fun givenGuestRoomLinkStatusIsSetToTrue_whenGettingItsValue_thenItShouldBeTrue() = runTest {
        userConfigDAO.persistGuestRoomLinkFeatureFlag(status = true, isStatusChanged = false)
        userConfigDAO.isGuestRoomLinkEnabled()?.status?.let {
            assertTrue { it }
        }
    }

    @Test
    fun givenScreenshotCensoringConfigIsSetToFalse_whenGettingItsValue_thenItShouldBeFalse() = runTest {
        userConfigDAO.persistScreenshotCensoring(enabled = false)
        assertEquals(false, userConfigDAO.isScreenshotCensoringEnabledFlow().first())
    }

    @Test
    fun givenScreenshotCensoringConfigIsSetToTrue_whenGettingItsValue_thenItShouldBeTrue() = runTest {
        userConfigDAO.persistScreenshotCensoring(enabled = true)
        assertEquals(true, userConfigDAO.isScreenshotCensoringEnabledFlow().first())
    }

    @Test
    fun givenAppLockConfig_whenStoring_thenItCanBeRead() = runTest {
        val expected = AppLockConfigEntity(
            enforceAppLock = true,
            inactivityTimeoutSecs = 60,
            isStatusChanged = false
        )
        userConfigDAO.persistAppLockStatus(
            expected.enforceAppLock,
            expected.inactivityTimeoutSecs,
            expected.isStatusChanged
        )
        assertEquals(expected, userConfigDAO.appLockStatus())
    }

    @Test
    fun givenNewAppLockValueStored_whenObservingFlow_thenNewValueIsEmitted() = runTest {
        userConfigDAO.appLockFlow().test {

            awaitItem().also {
                assertNull(it)
            }
            val expected1 = AppLockConfigEntity(
                enforceAppLock = true,
                inactivityTimeoutSecs = 60,
                isStatusChanged = true
            )
            userConfigDAO.persistAppLockStatus(
                expected1.enforceAppLock,
                expected1.inactivityTimeoutSecs,
                expected1.isStatusChanged
            )
            awaitItem().also {
                assertEquals(expected1, it)
            }

            val expected2 = AppLockConfigEntity(
                enforceAppLock = false,
                inactivityTimeoutSecs = 60,
                isStatusChanged = false
            )
            userConfigDAO.persistAppLockStatus(
                expected2.enforceAppLock,
                expected2.inactivityTimeoutSecs,
                expected2.isStatusChanged
            )
            awaitItem().also {
                assertEquals(expected2, it)
            }
        }
    }

    @Test
    fun givenDefaultProtocolIsNotSet_whenGettingItsValue_thenItShouldBeProteus() = runTest {
        assertEquals(SupportedProtocolEntity.PROTEUS, userConfigDAO.defaultProtocol())
    }

    @Test
    fun givenDefaultProtocolIsSetToMls_whenGettingItsValue_thenItShouldBeMls() = runTest {
        userConfigDAO.persistDefaultProtocol(SupportedProtocolEntity.MLS)
        assertEquals(SupportedProtocolEntity.MLS, userConfigDAO.defaultProtocol())
    }
}
