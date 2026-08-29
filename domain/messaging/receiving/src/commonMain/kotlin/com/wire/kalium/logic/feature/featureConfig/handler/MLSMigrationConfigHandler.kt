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
import com.wire.kalium.logic.configuration.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.feature.mlsmigration.hasMigrationEnded
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public class MLSMigrationConfigHandler(
    private val userConfigRepository: FeatureConfigRepository,
    private val updateSupportedProtocolsAndResolveOneOnOnes: FeatureConfigSupportedProtocolsUpdater,
    private val transactionProvider: FeatureConfigTransactionProvider,
) {

    public suspend fun handle(
        mlsMigrationConfig: MLSMigrationModel,
        duringSlowSync: Boolean,
        transactionContext: CryptoTransactionContext? = null
    ): Either<CoreFailure, Unit> {
        if (mlsMigrationConfig.hasMigrationEnded() && !duringSlowSync) {
            if (transactionContext != null) {
                updateSupportedProtocolsAndResolveOneOnOnes(
                    synchroniseUsers = true,
                    transactionContext = transactionContext
                )
            } else {
                transactionProvider.transaction("MLSMigrationConfig") { ctx ->
                    updateSupportedProtocolsAndResolveOneOnOnes(
                        synchroniseUsers = true,
                        transactionContext = ctx
                    )
                }
            }
        }

        return userConfigRepository.setMigrationConfiguration(mlsMigrationConfig)
    }
}
