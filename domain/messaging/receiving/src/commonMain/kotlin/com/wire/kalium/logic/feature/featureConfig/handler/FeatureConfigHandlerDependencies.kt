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

package com.wire.kalium.logic.feature.featureConfig.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public fun interface FeatureConfigSupportedProtocolsUpdater {
    public suspend operator fun invoke(
        transactionContext: CryptoTransactionContext,
        synchroniseUsers: Boolean,
    ): Either<CoreFailure, Unit>
}

@InternalKaliumApi
public interface FeatureConfigTransactionProvider {
    public suspend fun <R> transaction(
        name: String? = null,
        block: suspend (CryptoTransactionContext) -> Either<CoreFailure, R>,
    ): Either<CoreFailure, R>
}

@InternalKaliumApi
public fun interface MeetingsSlowSyncRepository {
    public suspend fun clearLastSlowSyncCompletionInstant()
}

@InternalKaliumApi
public class MeetingsSlowSyncRepositoryImpl public constructor(
    private val metadataDAO: MetadataDAO,
) : MeetingsSlowSyncRepository {
    override suspend fun clearLastSlowSyncCompletionInstant() {
        metadataDAO.deleteValue(key = LAST_SLOW_SYNC_INSTANT_KEY)
    }

    private companion object {
        const val LAST_SLOW_SYNC_INSTANT_KEY = "lastSlowSyncInstant"
    }
}
