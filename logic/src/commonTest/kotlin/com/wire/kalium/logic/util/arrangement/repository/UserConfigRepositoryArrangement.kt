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
import io.mockative.given
import io.mockative.mock

internal interface UserConfigRepositoryArrangement {
    val userConfigRepository: UserConfigRepository

    fun withGetSupportedProtocolsReturning(result: Either<StorageFailure, Set<SupportedProtocol>>)
    fun withSetSupportedProtocolsSuccessful()
    fun withSetDefaultProtocolSuccessful()
    fun withSetMLSEnabledSuccessful()
    fun withSetMigrationConfigurationSuccessful()
    fun withGetMigrationConfigurationReturning(result: Either<StorageFailure, MLSMigrationModel>)
    fun withSetSupportedCipherSuite(result: Either<StorageFailure, Unit>)
    fun withGetSupportedCipherSuitesReturning(result: Either<StorageFailure, SupportedCipherSuite>)
}

internal class UserConfigRepositoryArrangementImpl : UserConfigRepositoryArrangement {
    @Mock
    override val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

    override fun withGetSupportedProtocolsReturning(result: Either<StorageFailure, Set<SupportedProtocol>>) {
        given(userConfigRepository)
            .suspendFunction(userConfigRepository::getSupportedProtocols)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withSetSupportedProtocolsSuccessful() {
        given(userConfigRepository)
            .suspendFunction(userConfigRepository::setSupportedProtocols)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
    }

    override fun withSetDefaultProtocolSuccessful() {
        given(userConfigRepository)
            .function(userConfigRepository::setDefaultProtocol)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
    }

    override fun withSetMLSEnabledSuccessful() {
        given(userConfigRepository)
            .function(userConfigRepository::setMLSEnabled)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
    }

    override fun withSetMigrationConfigurationSuccessful() {
        given(userConfigRepository)
            .suspendFunction(userConfigRepository::setMigrationConfiguration)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
    }

    override fun withGetMigrationConfigurationReturning(result: Either<StorageFailure, MLSMigrationModel>) {
        given(userConfigRepository)
            .suspendFunction(userConfigRepository::getMigrationConfiguration)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withSetSupportedCipherSuite(result: Either<StorageFailure, Unit>) {
        given(userConfigRepository)
            .suspendFunction(userConfigRepository::setSupportedCipherSuite)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withGetSupportedCipherSuitesReturning(result: Either<StorageFailure, SupportedCipherSuite>) {
        given(userConfigRepository)
            .suspendFunction(userConfigRepository::getSupportedCipherSuite)
            .whenInvoked()
            .thenReturn(result)
    }
}
