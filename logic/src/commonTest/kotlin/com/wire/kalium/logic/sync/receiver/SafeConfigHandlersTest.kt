/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.GuestRoomLinkStatus
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.persistence.config.inMemoryUserConfigStorage
import com.wire.kalium.persistence.dao.UserConfigDAO
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SafeConfigHandlersTest {
    @Test
    fun strictConfigurationReadFailuresPreventOverwritingPreviousState() = runTest {
        val repository = mock<UserConfigRepository>()
        val failure = Either.Left(StorageFailure.Generic(IllegalStateException("read failed")))
        everySuspend { repository.getFileSharingServerStatus() } returns failure
        everySuspend { repository.getGuestRoomLinkStatus() } returns failure
        everySuspend { repository.getTeamSettingsSelfDeletionStatus() } returns failure
        everySuspend { repository.isTeamAppLockEnabled() } returns failure
        assertEquals(failure, FileSharingConfigHandler(repository).handle(ConfigsStatusModel(Status.ENABLED), requirePreviousState = true))
        assertEquals(
            failure,
            GuestRoomConfigHandler(repository, true).handle(ConfigsStatusModel(Status.ENABLED), requirePreviousState = true)
        )
        assertEquals(
            failure,
            SelfDeletingMessagesConfigHandler(repository, true).handle(selfDeletingModel, requirePreviousState = true)
        )
        assertEquals(failure, AppLockConfigHandler(repository).handle(AppLockModel(Status.ENABLED, 45), requirePreviousState = true))
        verifySuspend(VerifyMode.not) {
            repository.setFileSharingStatus(any(), any())
            repository.setGuestRoomStatus(any(), any())
            repository.setTeamSettingsSelfDeletionStatus(any())
            repository.setAppLockStatus(any(), any(), any())
        }
    }

    @Test
    fun appAppliesServerSettingsAfterPreviousStateReadFailures() = runTest {
        val repository = repository(StorageFailure.Generic(IllegalStateException("read failed")))

        assertEquals(Either.Right(Unit), FileSharingConfigHandler(repository).handle(ConfigsStatusModel(Status.ENABLED)))
        assertEquals(Either.Right(Unit), GuestRoomConfigHandler(repository, true).handle(ConfigsStatusModel(Status.ENABLED)))
        assertEquals(Either.Right(Unit), SelfDeletingMessagesConfigHandler(repository, true).handle(selfDeletingModel))
        assertEquals(Either.Right(Unit), AppLockConfigHandler(repository).handle(AppLockModel(Status.ENABLED, 45)))

        verifySuspend(VerifyMode.exactly(1)) {
            repository.setFileSharingStatus(true, false)
            repository.setGuestRoomStatus(true, false)
            repository.setTeamSettingsSelfDeletionStatus(selfDeletingStatus)
            repository.setAppLockStatus(true, 45, false)
        }
    }

    @Test
    fun missingPreviousStateAllowsStrictConfigurationToBeInitialized() = runTest {
        val repository = repository()

        assertEquals(Either.Right(Unit), FileSharingConfigHandler(repository).handle(ConfigsStatusModel(Status.ENABLED), true))
        assertEquals(Either.Right(Unit), GuestRoomConfigHandler(repository, true).handle(ConfigsStatusModel(Status.ENABLED), true))
        assertEquals(Either.Right(Unit), SelfDeletingMessagesConfigHandler(repository, true).handle(selfDeletingModel, true))
        assertEquals(Either.Right(Unit), AppLockConfigHandler(repository).handle(AppLockModel(Status.ENABLED, 45), true))

        verifySuspend(VerifyMode.exactly(1)) {
            repository.setFileSharingStatus(true, false)
            repository.setGuestRoomStatus(true, false)
            repository.setTeamSettingsSelfDeletionStatus(selfDeletingStatus)
            repository.setAppLockStatus(true, 45, true)
        }
    }

    @Test
    fun configurationWritesRemainRequiredForBothAppAndStrictCallers() = runTest {
        val failure = Either.Left(StorageFailure.Generic(IllegalStateException("write failed")))
        for (requirePreviousState in listOf(false, true)) {
            val repository = repository()
            everySuspend { repository.setFileSharingStatus(any(), any()) } returns failure
            everySuspend { repository.setGuestRoomStatus(any(), any()) } returns failure
            everySuspend { repository.setTeamSettingsSelfDeletionStatus(any()) } returns failure
            everySuspend { repository.setAppLockStatus(any(), any(), any()) } returns failure

            assertEquals(failure, FileSharingConfigHandler(repository).handle(ConfigsStatusModel(Status.ENABLED), requirePreviousState))
            assertEquals(failure, GuestRoomConfigHandler(repository, true).handle(ConfigsStatusModel(Status.ENABLED), requirePreviousState))
            assertEquals(failure, SelfDeletingMessagesConfigHandler(repository, true).handle(selfDeletingModel, requirePreviousState))
            assertEquals(failure, AppLockConfigHandler(repository).handle(AppLockModel(Status.ENABLED, 45), requirePreviousState))
        }
    }

    @Test
    fun replayRetainsGuestRoomAndSelfDeletingPendingChangesForBothPolicies() = runTest {
        for (requirePreviousState in listOf(false, true)) {
            val repository = repository()
            everySuspend { repository.getGuestRoomLinkStatus() } returns Either.Right(GuestRoomLinkStatus(true, true))
            everySuspend { repository.getTeamSettingsSelfDeletionStatus() } returns
                Either.Right(selfDeletingStatus.copy(hasFeatureChanged = true))

            assertEquals(
                Either.Right(Unit),
                GuestRoomConfigHandler(repository, true).handle(ConfigsStatusModel(Status.ENABLED), requirePreviousState)
            )
            assertEquals(
                Either.Right(Unit),
                SelfDeletingMessagesConfigHandler(repository, true).handle(selfDeletingModel, requirePreviousState)
            )

            verifySuspend(VerifyMode.exactly(1)) {
                repository.setGuestRoomStatus(true, true)
                repository.setTeamSettingsSelfDeletionStatus(selfDeletingStatus.copy(hasFeatureChanged = true))
            }
        }
    }

    @Test
    fun replayRetainsFileSharingChangeWithBuildRestrictions() = runTest {
        val storage = inMemoryUserConfigStorage()
        val repository = UserConfigDataSource(storage, mock<UserConfigDAO>(), lazy {
            BuildFileRestrictionState.AllowSome(listOf("txt"))
        })
        storage.persistFileSharingStatus(false, false)
        val handler = FileSharingConfigHandler(repository)
        repeat(2) { assertIs<Either.Right<*>>(handler.handle(ConfigsStatusModel(Status.ENABLED))) }
        assertTrue(checkNotNull(storage.isFileSharingEnabled()).isStatusChanged == true)
        // Presentation still follows the app's build policy.
        assertEquals(false, assertIs<Either.Right<FileSharingStatus>>(repository.isFileSharingEnabled()).value.isStatusChanged)
    }

    @Test
    fun disabledBuildSwitchesPreserveAppConfigurationBehavior() = runTest {
        val repository = mock<UserConfigRepository>()
        everySuspend { repository.setGuestRoomStatus(false, null) } returns Either.Right(Unit)
        everySuspend {
            repository.setTeamSettingsSelfDeletionStatus(
                TeamSettingsSelfDeletionStatus(hasFeatureChanged = null, enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Disabled)
            )
        } returns Either.Right(Unit)
        assertIs<Either.Right<*>>(GuestRoomConfigHandler(repository, false).handle(ConfigsStatusModel(Status.ENABLED)))
        assertIs<Either.Right<*>>(
            SelfDeletingMessagesConfigHandler(repository, false).handle(
                SelfDeletingMessagesModel(SelfDeletingMessagesConfigModel(60), Status.ENABLED)
            )
        )
    }

    private val selfDeletingModel = SelfDeletingMessagesModel(SelfDeletingMessagesConfigModel(60), Status.ENABLED)
    private val selfDeletingStatus = TeamSettingsSelfDeletionStatus(false, TeamSelfDeleteTimer.Enforced(60.seconds))

    private suspend fun repository(previousFailure: StorageFailure = StorageFailure.DataNotFound): UserConfigRepository =
        mock<UserConfigRepository>().also { repository ->
            everySuspend { repository.getFileSharingServerStatus() } returns Either.Left(previousFailure)
            everySuspend { repository.getGuestRoomLinkStatus() } returns Either.Left(previousFailure)
            everySuspend { repository.getTeamSettingsSelfDeletionStatus() } returns Either.Left(previousFailure)
            everySuspend { repository.isTeamAppLockEnabled() } returns Either.Left(previousFailure)
            everySuspend { repository.setFileSharingStatus(any(), any()) } returns Either.Right(Unit)
            everySuspend { repository.setGuestRoomStatus(any(), any()) } returns Either.Right(Unit)
            everySuspend { repository.setTeamSettingsSelfDeletionStatus(any()) } returns Either.Right(Unit)
            everySuspend { repository.setAppLockStatus(any(), any(), any()) } returns Either.Right(Unit)
        }
}
