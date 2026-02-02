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

package com.wire.kalium.persistence.dao

import app.cash.turbine.test
import com.wire.kalium.persistence.config.AppLockConfigEntity
import com.wire.kalium.persistence.config.ClassifiedDomainsEntity
import com.wire.kalium.persistence.config.IsFileSharingEnabledEntity
import com.wire.kalium.persistence.config.IsGuestRoomLinkEnabledEntity
import io.mockative.any
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class UserPrefsDAOTest {

    @Test
    fun givenAFileSharingStatusValue_whenCAllPersistItSaveAnd_thenCanRestoreTheValueLocally() = runTest {
        val expected1 = IsFileSharingEnabledEntity(true, null)
        val expected2 = IsFileSharingEnabledEntity(false, null)
        val (_, userConfigStorage) = Arrangement()
            .withSerializablePutSucceeding()
            .withSerializableGetSucceeding("file_sharing", expected1)
            .arrange()
        userConfigStorage.persistFileSharingStatus(true, null)
        assertEquals(expected1, userConfigStorage.isFileSharingEnabled())

        Arrangement()
            .withSerializablePutSucceeding()
            .withSerializableGetSucceeding("file_sharing", expected2)
            .arrange()
            .let { (_, storage) ->
                storage.persistFileSharingStatus(false, null)
                assertEquals(expected2, storage.isFileSharingEnabled())
            }
    }

    @Test
    fun givenAClassifiedDomainsStatusValue_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        val expected = ClassifiedDomainsEntity(true, listOf("bella.com", "anta.wire"))
        val (_, userConfigStorage) = Arrangement()
            .withSerializablePutSucceeding()
            .withSerializableObserveSucceeding("enable_classified_domains", expected)
            .arrange()
        userConfigStorage.persistClassifiedDomainsStatus(true, listOf("bella.com", "anta.wire"))
        assertEquals(
            expected,
            userConfigStorage.isClassifiedDomainsEnabledFlow().first()
        )
    }

    @Test
    fun givenAClassifiedDomainsStatusNotExists_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withSerializableObserveReturningNull("enable_classified_domains")
            .arrange()

        userConfigStorage.isClassifiedDomainsEnabledFlow().test {
            val item = awaitItem()
            assertEquals(null, item)
            awaitComplete()
        }
    }

    @Test
    fun givenAConferenceCallingStatusValue_whenPersistingIt_saveAndThenRestoreTheValueLocally() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withMetadataInserted()
            .withMetadataRetrieved("enable_conference_calling", "true")
            .arrange()
        userConfigStorage.persistConferenceCalling(true)
        assertEquals(
            true,
            userConfigStorage.isConferenceCallingEnabled()
        )
    }

    @Test
    fun givenAReadReceiptsSetValue_whenPersistingIt_saveAndThenRestoreTheValueLocally() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withMetadataInserted()
            .withValueByKeyFlowSucceeding("enable_read_receipts", "true")
            .arrange()
        userConfigStorage.persistReadReceipts(true)
        assertTrue(userConfigStorage.areReadReceiptsEnabled().first())
    }

    @Test
    fun whenMarkingFileSharingAsNotified_thenIsChangedIsSetToFalse() = runTest {
        val statusWithChanged = IsFileSharingEnabledEntity(true, true)
        val statusWithoutChanged = IsFileSharingEnabledEntity(true, false)
        val (_, userConfigStorage) = Arrangement()
            .withSerializablePutSucceeding()
            .withSerializableGetSucceeding("file_sharing", statusWithChanged)
            .arrange()
        userConfigStorage.persistFileSharingStatus(true, true)

        Arrangement()
            .withSerializablePutSucceeding()
            .withSerializableGetSucceeding("file_sharing", statusWithoutChanged)
            .arrange()
            .let { (_, storage) ->
                storage.setFileSharingAsNotified()
                assertEquals(statusWithoutChanged, storage.isFileSharingEnabled())
            }
    }

    @Test
    fun givenPasswordChallengeRequirementIsNotSet_whenGettingItsValue_thenItShouldBeFalseByDefault() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withNoMetadataRetrieved("require_second_factor_password_challenge")
            .arrange()
        assertFalse {
            userConfigStorage.isSecondFactorPasswordChallengeRequired()
        }
    }

    @Test
    fun givenPasswordChallengeRequirementIsSetToFalse_whenGettingItsValue_thenItShouldBeFalse() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withMetadataInserted()
            .withMetadataRetrieved("require_second_factor_password_challenge", "false")
            .arrange()
        userConfigStorage.persistSecondFactorPasswordChallengeStatus(false)
        assertFalse {
            userConfigStorage.isSecondFactorPasswordChallengeRequired()
        }
    }

    @Test
    fun givenPasswordChallengeRequirementIsSetToTrue_whenGettingItsValue_thenItShouldBeTrue() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withMetadataInserted()
            .withMetadataRetrieved("require_second_factor_password_challenge", "true")
            .arrange()
        userConfigStorage.persistSecondFactorPasswordChallengeStatus(true)
        assertTrue {
            userConfigStorage.isSecondFactorPasswordChallengeRequired()
        }
    }

    @Test
    fun givenGuestRoomLinkStatusIsSetToFalse_whenGettingItsValue_thenItShouldBeFalse() = runTest {
        val expected = IsGuestRoomLinkEnabledEntity(false, false)
        val (_, userConfigStorage) = Arrangement()
            .withSerializablePutSucceeding()
            .withSerializableGetSucceeding("guest_room_link", expected)
            .arrange()
        userConfigStorage.persistGuestRoomLinkFeatureFlag(status = false, isStatusChanged = false)
        userConfigStorage.isGuestRoomLinkEnabled()?.status?.let {
            assertFalse { it }
        }
    }

    @Test
    fun givenGuestRoomLinkStatusIsSetToTrue_whenGettingItsValue_thenItShouldBeTrue() = runTest {
        val expected = IsGuestRoomLinkEnabledEntity(true, false)
        val (_, userConfigStorage) = Arrangement()
            .withSerializablePutSucceeding()
            .withSerializableGetSucceeding("guest_room_link", expected)
            .arrange()
        userConfigStorage.persistGuestRoomLinkFeatureFlag(status = true, isStatusChanged = false)
        userConfigStorage.isGuestRoomLinkEnabled()?.status?.let {
            assertTrue { it }
        }
    }

    @Test
    fun givenScreenshotCensoringConfigIsSetToFalse_whenGettingItsValue_thenItShouldBeFalse() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withMetadataInserted()
            .withValueByKeyFlowSucceeding("enable_screenshot_censoring", "false")
            .arrange()
        userConfigStorage.persistScreenshotCensoring(enabled = false)
        assertEquals(false, userConfigStorage.isScreenshotCensoringEnabledFlow().first())
    }

    @Test
    fun givenScreenshotCensoringConfigIsSetToTrue_whenGettingItsValue_thenItShouldBeTrue() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withMetadataInserted()
            .withValueByKeyFlowSucceeding("enable_screenshot_censoring", "true")
            .arrange()
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
        val (_, userConfigStorage) = Arrangement()
            .withSerializablePutSucceeding()
            .withSerializableGetSucceeding("app_lock", expected)
            .arrange()
        userConfigStorage.persistAppLockStatus(
            expected.enforceAppLock,
            expected.inactivityTimeoutSecs,
            expected.isStatusChanged
        )
        assertEquals(expected, userConfigStorage.appLockStatus())
    }

    @Test
    fun givenDefaultProtocolIsNotSet_whenGettingItsValue_thenItShouldBeProteus() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withNoMetadataRetrieved("default_protocol")
            .arrange()
        assertEquals(SupportedProtocolEntity.PROTEUS, userConfigStorage.defaultProtocol())
    }

    @Test
    fun givenDefaultProtocolIsSetToMls_whenGettingItsValue_thenItShouldBeMls() = runTest {
        val (_, userConfigStorage) = Arrangement()
            .withMetadataInserted()
            .withMetadataRetrieved("default_protocol", "MLS")
            .arrange()
        userConfigStorage.persistDefaultProtocol(SupportedProtocolEntity.MLS)
        assertEquals(SupportedProtocolEntity.MLS, userConfigStorage.defaultProtocol())
    }

    private class Arrangement {
        val metadataDAO = mock(MetadataDAO::class)

        suspend fun withMetadataInserted() = apply {
            coEvery { metadataDAO.insertValue(any(), any()) } returns Unit
        }

        suspend fun withMetadataRetrieved(key: String, value: String?) = apply {
            coEvery { metadataDAO.valueByKey(eq(key)) } returns value
        }

        suspend fun withNoMetadataRetrieved(key: String) = apply {
            coEvery { metadataDAO.valueByKey(eq(key)) } returns null
        }

        suspend fun withSerializablePutSucceeding() = apply {
            coEvery { metadataDAO.putSerializable(any<String>(), any<Any>(), any()) } returns Unit
        }

        suspend fun withSerializableGetSucceeding(key: String, value: Any?) = apply {
            coEvery { metadataDAO.getSerializable(eq(key), any<kotlinx.serialization.KSerializer<*>>()) } returns value
        }

        fun withSerializableObserveSucceeding(key: String, value: Any?) = apply {
            every { metadataDAO.observeSerializable(eq(key), any<kotlinx.serialization.KSerializer<*>>()) } returns flowOf(value)
        }

        fun withSerializableObserveReturningNull(key: String) = apply {
            every { metadataDAO.observeSerializable(eq(key), any<kotlinx.serialization.KSerializer<*>>()) } returns flowOf(null)
        }

        suspend fun withValueByKeyFlowSucceeding(key: String, value: String?) = apply {
            coEvery { metadataDAO.valueByKeyFlow(eq(key)) } returns flowOf(value)
        }

        fun arrange() = this to UserPrefsDAO(metadataDAO)

    }
}
