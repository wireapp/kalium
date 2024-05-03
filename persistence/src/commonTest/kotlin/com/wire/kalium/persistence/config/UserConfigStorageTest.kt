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
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertTrue(userConfigStorage.areReadReceiptsEnabled().first())
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
    fun givenScreenshotCensoringConfigIsSetToFalse_whenGettingItsValue_thenItShouldBeFalse() = runTest {
        userConfigStorage.persistScreenshotCensoring(enabled = false)
        assertEquals(false, userConfigStorage.isScreenshotCensoringEnabledFlow().first())
    }

    @Test
    fun givenScreenshotCensoringConfigIsSetToTrue_whenGettingItsValue_thenItShouldBeTrue() = runTest {
        userConfigStorage.persistScreenshotCensoring(enabled = true)
        assertEquals(true, userConfigStorage.isScreenshotCensoringEnabledFlow().first())
    }

    @Test
    fun givenAppLockConfig_whenStoring_thenItCanBeRead() = runTest {
        val expected = AppLockConfigEntity(
            enforceAppLock = true,
            inactivityTimeoutSecs = 60,
            isStatusChanged = false
        )
        userConfigStorage.persistAppLockStatus(
            expected.enforceAppLock,
            expected.inactivityTimeoutSecs,
            expected.isStatusChanged
        )
        assertEquals(expected, userConfigStorage.appLockStatus())
    }

    @Test
    fun givenNewAppLockValueStored_whenObservingFlow_thenNewValueIsEmitted() = runTest {
        userConfigStorage.appLockFlow().test {

            awaitItem().also {
                assertNull(it)
            }
            val expected1 = AppLockConfigEntity(
                enforceAppLock = true,
                inactivityTimeoutSecs = 60,
                isStatusChanged = true
            )
            userConfigStorage.persistAppLockStatus(
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
            userConfigStorage.persistAppLockStatus(
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
    fun givenDefaultProtocolIsNotSet_whenGettingItsValue_thenItShouldBeProteus() {
        assertEquals(SupportedProtocolEntity.PROTEUS, userConfigStorage.defaultProtocol())
    }

    @Test
    fun givenDefaultProtocolIsSetToMls_whenGettingItsValue_thenItShouldBeMls() {
        userConfigStorage.persistDefaultProtocol(SupportedProtocolEntity.MLS)
        assertEquals(SupportedProtocolEntity.MLS, userConfigStorage.defaultProtocol())
    }
}
