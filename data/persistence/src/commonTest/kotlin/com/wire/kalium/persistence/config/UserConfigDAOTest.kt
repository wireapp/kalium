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
package com.wire.kalium.persistence.config

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.unread.UserConfigDAO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun givenNoValueStoredForShouldFetchE2EITrustAnchorHasRun_whenCalled_thenReturnTrue() = runTest {
        assertTrue(userConfigDAO.getShouldFetchE2EITrustAnchorHasRun())
    }

    @Test
    fun givenShouldFetchE2EITrustAnchorHasRunIsSetToFalse_whenCalled_thenReturnFalse() = runTest {
        userConfigDAO.setShouldFetchE2EITrustAnchors(false)
        assertFalse(userConfigDAO.getShouldFetchE2EITrustAnchorHasRun())
    }

    @Test
    fun givenShouldFetchE2EITrustAnchorHasRunIsSetToTrue_whenCalled_thenReturnTrue() = runTest {
        userConfigDAO.setShouldFetchE2EITrustAnchors(true)
        assertTrue(userConfigDAO.getShouldFetchE2EITrustAnchorHasRun())
    }
}
