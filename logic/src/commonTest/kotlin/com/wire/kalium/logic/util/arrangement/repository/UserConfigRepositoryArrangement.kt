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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import io.mockative.any
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf

internal interface UserConfigRepositoryArrangement {
    val userConfigRepository: UserConfigRepository

    suspend fun withGetSupportedProtocolsReturning(result: Either<StorageFailure, Set<SupportedProtocol>>)
    suspend fun withSetSupportedProtocolsSuccessful()
    fun withSetDefaultProtocolSuccessful()
    fun withGetDefaultProtocolReturning(result: Either<StorageFailure, SupportedProtocol>)
    fun withSetMLSEnabledSuccessful()
    fun withGetMLSEnabledReturning(result: Either<StorageFailure, Boolean>)
    suspend fun withSetMigrationConfigurationSuccessful()
    suspend fun withGetMigrationConfigurationReturning(result: Either<StorageFailure, MLSMigrationModel>)
    suspend fun withSetSupportedCipherSuite(result: Either<StorageFailure, Unit>)
    suspend fun withGetSupportedCipherSuitesReturning(result: Either<StorageFailure, SupportedCipherSuite>)
    suspend fun withSetTrackingIdentifier()
    suspend fun withGetTrackingIdentifier(result: String?)
    suspend fun withSetPreviousTrackingIdentifier()
    suspend fun withGetPreviousTrackingIdentifier(result: String?)
    suspend fun withObserveTrackingIdentifier(result: Either<StorageFailure, String>)
    suspend fun withDeletePreviousTrackingIdentifier()
    suspend fun withUpdateNextTimeForCallFeedback()
    suspend fun withGetNextTimeForCallFeedback(result: Either<StorageFailure, Long>)
    suspend fun withConferenceCallingEnabled(result: Boolean)
}

internal class UserConfigRepositoryArrangementImpl : UserConfigRepositoryArrangement {

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

    override fun withGetMLSEnabledReturning(result: Either<StorageFailure, Boolean>) {
        every {
            userConfigRepository.isMLSEnabled()
        }.returns(result)
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
        coEvery { userConfigRepository.setCurrentTrackingIdentifier(any()) }.returns(Unit)
    }

    override suspend fun withGetTrackingIdentifier(result: String?) {
        coEvery { userConfigRepository.getCurrentTrackingIdentifier() }.returns(result)
    }

    override suspend fun withSetPreviousTrackingIdentifier() {
        coEvery { userConfigRepository.setPreviousTrackingIdentifier(any()) }.returns(Unit)
    }

    override suspend fun withGetPreviousTrackingIdentifier(result: String?) {
        coEvery { userConfigRepository.getPreviousTrackingIdentifier() }.returns(result)
    }

    override suspend fun withObserveTrackingIdentifier(result: Either<StorageFailure, String>) {
        coEvery { userConfigRepository.observeCurrentTrackingIdentifier() }.returns(flowOf(result))
    }

    override suspend fun withDeletePreviousTrackingIdentifier() {
        coEvery { userConfigRepository.deletePreviousTrackingIdentifier() }.returns(Unit)
    }

    override suspend fun withGetNextTimeForCallFeedback(result: Either<StorageFailure, Long>) {
        coEvery { userConfigRepository.getNextTimeForCallFeedback() }.returns(result)
    }

    override suspend fun withUpdateNextTimeForCallFeedback() {
        coEvery { userConfigRepository.updateNextTimeForCallFeedback(any()) }.returns(Unit)
    }

    override suspend fun withConferenceCallingEnabled(result: Boolean) {
        every { userConfigRepository.isConferenceCallingEnabled() }.returns(result.right())
    }
}
