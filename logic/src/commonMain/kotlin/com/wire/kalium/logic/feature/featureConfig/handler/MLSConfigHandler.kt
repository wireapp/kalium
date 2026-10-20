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
package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.di.UserSessionBinding
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import dev.zacsweers.metro.Inject

@Inject
internal class MLSConfigHandler(
    @UserSessionBinding("userConfigRepository")
    private val userConfigRepository: UserConfigRepository,
    @UserSessionBinding("updateSupportedProtocolsAndResolveOneOnOnes")
    private val updateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase,
    @UserSessionBinding("cryptoTransactionProvider")
    private val transactionProvider: CryptoTransactionProvider
) {
    internal suspend fun handle(
        mlsConfig: MLSModel,
        duringSlowSync: Boolean,
        transactionContext: CryptoTransactionContext? = null,
        duringClientRegistration: Boolean = false,
    ): Either<CoreFailure, Unit> = userConfigRepository.applyMLSConfiguration(mlsConfig) {
        if (duringClientRegistration) {
            // There is no registered crypto client yet. SlowSyncWorker updates supported protocols after registration.
            Either.Right(Unit)
        } else if (transactionContext != null) {
            updateSupportedProtocolsAndResolveOneOnOnes(transactionContext, !duringSlowSync)
        } else {
            transactionProvider.transaction("MLSConfigHandler") { ctx ->
                updateSupportedProtocolsAndResolveOneOnOnes(ctx, !duringSlowSync)
            }
        }
    }
}
