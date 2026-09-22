/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.AppLockModel
import com.wire.kalium.logic.data.featureConfig.ConfigsStatusModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesConfigModel
import com.wire.kalium.logic.data.featureConfig.SelfDeletingMessagesModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.persistence.config.inMemoryUserConfigStorage
import com.wire.kalium.persistence.dao.UserConfigDAO
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SafeConfigHandlersTest {
    @Test
    fun configurationReadFailuresRemainRequiredFailures() = runTest {
        val repository = mock<UserConfigRepository>()
        val failure = Either.Left(StorageFailure.Generic(IllegalStateException("read failed")))
        everySuspend { repository.getFileSharingServerStatus() } returns failure
        everySuspend { repository.getGuestRoomLinkStatus() } returns failure
        everySuspend { repository.getTeamSettingsSelfDeletionStatus() } returns failure
        everySuspend { repository.isTeamAppLockEnabled() } returns failure
        assertEquals(failure, FileSharingConfigHandler(repository).handle(ConfigsStatusModel(Status.ENABLED)))
        assertEquals(failure, GuestRoomConfigHandler(repository, true).handle(ConfigsStatusModel(Status.ENABLED)))
        assertEquals(
            failure,
            SelfDeletingMessagesConfigHandler(repository, true).handle(
            SelfDeletingMessagesModel(SelfDeletingMessagesConfigModel(60), Status.ENABLED)
        )
        )
        assertEquals(failure, AppLockConfigHandler(repository).handle(AppLockModel(Status.ENABLED, 45)))
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
            com.wire.kalium.logic.data.message.TeamSettingsSelfDeletionStatus(hasFeatureChanged = null, enforcedSelfDeletionTimer = TeamSelfDeleteTimer.Disabled)
        )
        } returns Either.Right(Unit)
        assertIs<Either.Right<*>>(GuestRoomConfigHandler(repository, false).handle(ConfigsStatusModel(Status.ENABLED)))
        assertIs<Either.Right<*>>(
            SelfDeletingMessagesConfigHandler(repository, false).handle(
            SelfDeletingMessagesModel(SelfDeletingMessagesConfigModel(60), Status.ENABLED)
        )
        )
    }
}
