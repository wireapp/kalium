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

package com.wire.kalium.logic.configuration

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.config.IsFileSharingEnabledEntity
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.dao.UserConfigDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserConfigDataSourceFileSharingTest {

    @Test
    fun givenEnabledServerConfigAndNoBuildRestriction_whenObserving_thenEnabledAllIsReturned() = runTest {
        val repository = repository(
            serverSideConfig = IsFileSharingEnabledEntity(status = true, isStatusChanged = true),
            buildRestriction = BuildFileRestrictionState.NoRestriction,
        )

        assertEquals(
            Either.Right(FileSharingStatus(FileSharingStatus.Value.EnabledAll, isStatusChanged = true)),
            repository.isFileSharingEnabledFlow().first(),
        )
    }

    @Test
    fun givenEnabledServerConfigAndAllowedTypes_whenObserving_thenEnabledSomeIsReturned() = runTest {
        val allowedTypes = listOf("png", "jpg")
        val repository = repository(
            serverSideConfig = IsFileSharingEnabledEntity(status = true, isStatusChanged = true),
            buildRestriction = BuildFileRestrictionState.AllowSome(allowedTypes),
        )

        assertEquals(
            Either.Right(FileSharingStatus(FileSharingStatus.Value.EnabledSome(allowedTypes), isStatusChanged = false)),
            repository.isFileSharingEnabledFlow().first(),
        )
    }

    @Test
    fun givenDisabledServerConfigAndAllowedTypes_whenObserving_thenDisabledIsReturned() = runTest {
        val repository = repository(
            serverSideConfig = IsFileSharingEnabledEntity(status = false, isStatusChanged = true),
            buildRestriction = BuildFileRestrictionState.AllowSome(listOf("png")),
        )

        assertEquals(
            Either.Right(FileSharingStatus(FileSharingStatus.Value.Disabled, isStatusChanged = true)),
            repository.isFileSharingEnabledFlow().first(),
        )
    }

    @Test
    fun givenMissingServerConfig_whenObserving_thenBuildRestrictionIsEvaluatedAndFailureIsReturned() = runTest {
        var restrictionEvaluationCount = 0
        val storage = storageReturning(null)
        val repository = UserConfigDataSource(
            userConfigStorage = storage,
            userConfigDAO = mock(mode = MockMode.autoUnit),
            kaliumConfigs = KaliumConfigs(
                fileRestrictionState = lazy {
                    restrictionEvaluationCount++
                    BuildFileRestrictionState.NoRestriction
                }
            ),
        )

        assertEquals(
            Either.Left(StorageFailure.DataNotFound),
            repository.isFileSharingEnabledFlow().first(),
        )
        assertEquals(1, restrictionEvaluationCount)
    }

    private fun repository(
        serverSideConfig: IsFileSharingEnabledEntity?,
        buildRestriction: BuildFileRestrictionState,
    ): UserConfigRepository = UserConfigDataSource(
        userConfigStorage = storageReturning(serverSideConfig),
        userConfigDAO = mock(mode = MockMode.autoUnit),
        kaliumConfigs = KaliumConfigs(fileRestrictionState = lazy { buildRestriction }),
    )

    private fun storageReturning(serverSideConfig: IsFileSharingEnabledEntity?): UserConfigStorage =
        mock(mode = MockMode.autoUnit) {
            every { isFileSharingEnabledFlow() } returns flowOf(serverSideConfig)
        }
}
