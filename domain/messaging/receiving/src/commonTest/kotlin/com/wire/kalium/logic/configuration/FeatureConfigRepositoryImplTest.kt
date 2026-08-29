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
import com.wire.kalium.logic.data.featureConfig.CellsInternalConfigModel
import com.wire.kalium.logic.data.featureConfig.CollaboraEdition
import com.wire.kalium.persistence.config.IsFileSharingEnabledEntity
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.config.WireCellsConfigEntity
import com.wire.kalium.persistence.dao.UserConfigDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureConfigRepositoryImplTest {

    @Test
    fun givenAllowedFileTypesProvider_whenRepositoryIsConstructed_thenProviderIsNotEvaluated() {
        var evaluationCount = 0

        repository {
            evaluationCount++
            null
        }

        assertEquals(0, evaluationCount)
    }

    @Test
    fun givenFileSharingEnabledAndNoRestriction_whenQueried_thenReturnsEnabledAll() = runTest {
        val repository = repositoryReturning(status = true, isStatusChanged = true) { null }

        val result = repository.isFileSharingEnabled()

        assertEquals(
            Either.Right(FileSharingStatus(FileSharingStatus.Value.EnabledAll, isStatusChanged = true)),
            result,
        )
    }

    @Test
    fun givenFileSharingEnabledAndAllowedTypes_whenQueried_thenReturnsEnabledSome() = runTest {
        val allowedFileTypes = listOf("image/png", "image/jpeg")
        val repository = repositoryReturning(status = true, isStatusChanged = true) { allowedFileTypes }
        val provider: FileSharingStatusProvider = repository

        val result = provider.isFileSharingEnabled()

        assertEquals(
            Either.Right(
                FileSharingStatus(FileSharingStatus.Value.EnabledSome(allowedFileTypes), isStatusChanged = false)
            ),
            result,
        )
    }

    @Test
    fun givenFileSharingDisabled_whenQueried_thenReturnsDisabled() = runTest {
        var evaluationCount = 0
        val repository = repositoryReturning(status = false, isStatusChanged = true) {
            evaluationCount++
            listOf("image/png")
        }

        val result = repository.isFileSharingEnabled()

        assertEquals(
            Either.Right(FileSharingStatus(FileSharingStatus.Value.Disabled, isStatusChanged = true)),
            result,
        )
        assertEquals(1, evaluationCount)
    }

    @Test
    fun givenStorageFailure_whenQueried_thenEvaluatesProviderAndReturnsFailure() = runTest {
        val expectedException = IllegalStateException("storage failure")
        val userConfigStorage = mock<UserConfigStorage> {
            everySuspend { isFileSharingEnabled() } throws expectedException
        }
        var evaluationCount = 0
        val repository = repository(userConfigStorage) {
            evaluationCount++
            null
        }

        val result = repository.isFileSharingEnabled()

        assertEquals(Either.Left(StorageFailure.Generic(expectedException)), result)
        assertEquals(1, evaluationCount)
    }

    @Test
    fun givenInternalCellsConfig_whenPersisted_thenExactEntityIsStored() = runTest {
        val userConfigDAO = mock<UserConfigDAO>(MockMode.autoUnit)
        val repository = repository(userConfigDAO = userConfigDAO) { null }
        val config = CellsInternalConfigModel(
            backendUrl = "https://cells.example.com",
            collaboraEdition = CollaboraEdition.CODE,
            perUserQuotaBytes = 42L,
        )

        val result = repository.persistInternalCellsConfig(config)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            userConfigDAO.setWireCellsConfig(
                WireCellsConfigEntity(
                    backendUrl = "https://cells.example.com",
                    collabora = "CODE",
                    teamQuotaBytes = 42L,
                )
            )
        }
        verifySuspend(VerifyMode.not) { userConfigDAO.removeWireCellsConfig() }
    }

    @Test
    fun givenNullInternalCellsConfig_whenPersisted_thenStoredConfigIsRemoved() = runTest {
        val userConfigDAO = mock<UserConfigDAO>(MockMode.autoUnit)
        val repository = repository(userConfigDAO = userConfigDAO) { null }

        val result = repository.persistInternalCellsConfig(null)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) { userConfigDAO.removeWireCellsConfig() }
    }

    @Test
    fun givenCellsStorageFailure_whenPersisted_thenFailureIsReturned() = runTest {
        val expectedException = IllegalStateException("cells storage failure")
        val config = CellsInternalConfigModel(
            backendUrl = null,
            collaboraEdition = CollaboraEdition.NO,
            perUserQuotaBytes = null,
        )
        val expectedEntity = WireCellsConfigEntity(null, "NO", null)
        val userConfigDAO = mock<UserConfigDAO> {
            everySuspend { setWireCellsConfig(expectedEntity) } throws expectedException
        }
        val repository = repository(userConfigDAO = userConfigDAO) { null }

        val result = repository.persistInternalCellsConfig(config)

        assertEquals(Either.Left(StorageFailure.Generic(expectedException)), result)
    }

    private fun repositoryReturning(
        status: Boolean,
        isStatusChanged: Boolean?,
        allowedFileTypesProvider: () -> List<String>?,
    ): FeatureConfigRepositoryImpl {
        val userConfigStorage = mock<UserConfigStorage> {
            everySuspend { isFileSharingEnabled() } returns IsFileSharingEnabledEntity(status, isStatusChanged)
        }
        return repository(userConfigStorage, allowedFileTypesProvider = allowedFileTypesProvider)
    }

    private fun repository(
        userConfigStorage: UserConfigStorage = mock(),
        userConfigDAO: UserConfigDAO = mock(),
        allowedFileTypesProvider: () -> List<String>?,
    ): FeatureConfigRepositoryImpl = FeatureConfigRepositoryImpl(
        userConfigStorage = userConfigStorage,
        userConfigDAO = userConfigDAO,
        allowedFileTypesProvider = allowedFileTypesProvider,
    )
}
