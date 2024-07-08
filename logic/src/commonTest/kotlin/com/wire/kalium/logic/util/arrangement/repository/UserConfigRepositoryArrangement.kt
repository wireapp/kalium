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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock

internal interface UserConfigRepositoryArrangement {
    val userConfigRepository: UserConfigRepository

    suspend fun withGetSupportedProtocolsReturning(result: Either<StorageFailure, Set<SupportedProtocol>>)
    suspend fun withSetSupportedProtocolsSuccessful()
    fun withSetDefaultProtocolSuccessful()
    fun withGetDefaultProtocolReturning(result: Either<StorageFailure, SupportedProtocol>)
    fun withSetMLSEnabledSuccessful()
    suspend fun withSetMigrationConfigurationSuccessful()
    suspend fun withGetMigrationConfigurationReturning(result: Either<StorageFailure, MLSMigrationModel>)
    suspend fun withSetSupportedCipherSuite(result: Either<StorageFailure, Unit>)
    suspend fun withGetSupportedCipherSuitesReturning(result: Either<StorageFailure, SupportedCipherSuite>)
    suspend fun withSetTrackingIdentifier()
}

internal class UserConfigRepositoryArrangementImpl : UserConfigRepositoryArrangement {
    @Mock
    override val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

    override suspend fun withGetSupportedProtocolsReturning(result: Either<StorageFailure, Set<SupportedProtocol>>) {
        coEvery {
            userConfigRepository.getSupportedProtocols()
        }.returns(result)
    }

    override suspend fun withSetSupportedProtocolsSuccessful() {
        coEvery {
            userConfigRepository.setSupportedProtocols(any())
        }.returns(Either.Right(Unit))
    }

    override fun withSetDefaultProtocolSuccessful() {
        every {
            userConfigRepository.setDefaultProtocol(any())
        }.returns(Either.Right(Unit))
    }

    override fun withGetDefaultProtocolReturning(result: Either<StorageFailure, SupportedProtocol>) {
        every { userConfigRepository.getDefaultProtocol() }.returns(result)
    }

    override fun withSetMLSEnabledSuccessful() {
        every {
            userConfigRepository.setMLSEnabled(any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withGetSupportedCipherSuitesReturning(result: Either<StorageFailure, SupportedCipherSuite>) {
        coEvery { userConfigRepository.getSupportedCipherSuite() }.returns(result)
    }

    override suspend fun withSetMigrationConfigurationSuccessful() {
        coEvery {
            userConfigRepository.setMigrationConfiguration(any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withGetMigrationConfigurationReturning(result: Either<StorageFailure, MLSMigrationModel>) {
        coEvery {
            userConfigRepository.getMigrationConfiguration()
        }.returns(result)
    }

    override suspend fun withSetSupportedCipherSuite(result: Either<StorageFailure, Unit>) {
        coEvery { userConfigRepository.setSupportedCipherSuite(any()) }.returns(result)
    }

    override suspend fun withSetTrackingIdentifier() {
        coEvery { userConfigRepository.setTrackingIdentifier(any()) }.returns(Unit)
    }
}
