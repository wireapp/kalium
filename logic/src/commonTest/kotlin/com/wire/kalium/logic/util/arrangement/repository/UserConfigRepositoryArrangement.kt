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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf

internal interface UserConfigRepositoryArrangement {
    val userConfigRepository: UserConfigRepository

    suspend fun withGetSupportedProtocolsReturning(result: Either<StorageFailure, Set<SupportedProtocol>>)
    suspend fun withSetSupportedProtocolsSuccessful()
    suspend fun withSetDefaultProtocolSuccessful()
    suspend fun withGetDefaultProtocolReturning(result: Either<StorageFailure, SupportedProtocol>)
    suspend fun withSetMLSEnabledSuccessful()
    suspend fun withGetMLSEnabledReturning(result: Either<StorageFailure, Boolean>)
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
    suspend fun withDeleteLegalHoldRequestSuccess(): UserConfigRepositoryArrangement
    suspend fun withSetLegalHoldChangeNotifiedSuccess(): UserConfigRepositoryArrangement
}

internal class UserConfigRepositoryArrangementImpl : UserConfigRepositoryArrangement {

    override val userConfigRepository: UserConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)

    override suspend fun withGetSupportedProtocolsReturning(result: Either<StorageFailure, Set<SupportedProtocol>>) {
        everySuspend {
            userConfigRepository.getSupportedProtocols()
        }.returns(result)
    }

    override suspend fun withSetSupportedProtocolsSuccessful() {
        everySuspend {
            userConfigRepository.setSupportedProtocols(any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withSetDefaultProtocolSuccessful() {
        everySuspend {
            userConfigRepository.setDefaultProtocol(any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withGetDefaultProtocolReturning(result: Either<StorageFailure, SupportedProtocol>) {
        everySuspend { userConfigRepository.getDefaultProtocol() }.returns(result)
    }

    override suspend fun withSetMLSEnabledSuccessful() {
        everySuspend {
            userConfigRepository.setMLSEnabled(any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withGetMLSEnabledReturning(result: Either<StorageFailure, Boolean>) {
        everySuspend {
            userConfigRepository.isMLSEnabled()
        }.returns(result)
    }

    override suspend fun withGetSupportedCipherSuitesReturning(result: Either<StorageFailure, SupportedCipherSuite>) {
        everySuspend { userConfigRepository.getSupportedCipherSuite() }.returns(result)
    }

    override suspend fun withSetMigrationConfigurationSuccessful() {
        everySuspend {
            userConfigRepository.setMigrationConfiguration(any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withGetMigrationConfigurationReturning(result: Either<StorageFailure, MLSMigrationModel>) {
        everySuspend {
            userConfigRepository.getMigrationConfiguration()
        }.returns(result)
    }

    override suspend fun withSetSupportedCipherSuite(result: Either<StorageFailure, Unit>) {
        everySuspend { userConfigRepository.setSupportedCipherSuite(any()) }.returns(result)
    }

    override suspend fun withSetTrackingIdentifier() {
        everySuspend { userConfigRepository.setCurrentTrackingIdentifier(any()) }.returns(Unit)
    }

    override suspend fun withGetTrackingIdentifier(result: String?) {
        everySuspend { userConfigRepository.getCurrentTrackingIdentifier() }.returns(result)
    }

    override suspend fun withSetPreviousTrackingIdentifier() {
        everySuspend { userConfigRepository.setPreviousTrackingIdentifier(any()) }.returns(Unit)
    }

    override suspend fun withGetPreviousTrackingIdentifier(result: String?) {
        everySuspend { userConfigRepository.getPreviousTrackingIdentifier() }.returns(result)
    }

    override suspend fun withObserveTrackingIdentifier(result: Either<StorageFailure, String>) {
        everySuspend { userConfigRepository.observeCurrentTrackingIdentifier() }.returns(flowOf(result))
    }

    override suspend fun withDeletePreviousTrackingIdentifier() {
        everySuspend { userConfigRepository.deletePreviousTrackingIdentifier() }.returns(Unit)
    }

    override suspend fun withGetNextTimeForCallFeedback(result: Either<StorageFailure, Long>) {
        everySuspend { userConfigRepository.getNextTimeForCallFeedback() }.returns(result)
    }

    override suspend fun withUpdateNextTimeForCallFeedback() {
        everySuspend { userConfigRepository.updateNextTimeForCallFeedback(any()) }.returns(Unit)
    }

    override suspend fun withConferenceCallingEnabled(result: Boolean) {
        everySuspend { userConfigRepository.isConferenceCallingEnabled() }.returns(result.right())
    }

    override suspend fun withDeleteLegalHoldRequestSuccess() = apply {
        everySuspend { userConfigRepository.deleteLegalHoldRequest() }.returns(Either.Right(Unit))
    }

    override suspend fun withSetLegalHoldChangeNotifiedSuccess() = apply {
        everySuspend { userConfigRepository.setLegalHoldChangeNotified(any()) }.returns(Either.Right(Unit))
    }
}
