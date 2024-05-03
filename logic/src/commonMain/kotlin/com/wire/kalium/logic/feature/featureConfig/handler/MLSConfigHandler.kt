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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrElse

class MLSConfigHandler(
    private val userConfigRepository: UserConfigRepository,
    private val updateSupportedProtocolsAndResolveOneOnOnes: UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
) {
    suspend fun handle(mlsConfig: MLSModel, duringSlowSync: Boolean): Either<CoreFailure, Unit> {
        val mlsEnabled = mlsConfig.status == Status.ENABLED
        val isMLSSupported = mlsConfig.supportedProtocols.contains(SupportedProtocol.MLS)
        val previousSupportedProtocols = userConfigRepository.getSupportedProtocols().getOrElse(setOf(SupportedProtocol.PROTEUS))
        val supportedProtocolsHasChanged = !previousSupportedProtocols.equals(mlsConfig.supportedProtocols)

        return userConfigRepository.setMLSEnabled(mlsEnabled && isMLSSupported)
            .flatMap {
                userConfigRepository.setDefaultProtocol(if (mlsEnabled) mlsConfig.defaultProtocol else SupportedProtocol.PROTEUS)
            }.flatMap {
                userConfigRepository.setSupportedProtocols(mlsConfig.supportedProtocols)
            }.flatMap {
                if (supportedProtocolsHasChanged) {
                    updateSupportedProtocolsAndResolveOneOnOnes(
                        synchroniseUsers = !duringSlowSync
                    )
                } else {
                    Either.Right(Unit)
                }
            }
    }
}
