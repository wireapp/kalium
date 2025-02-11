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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapProteusRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for clearing the client data.
 * The proteus client will be cleared and the MLS client will be cleared.
 */
interface ClearClientDataUseCase {
    suspend operator fun invoke()
}

internal class ClearClientDataUseCaseImpl internal constructor(
    private val mlsClientProvider: MLSClientProvider,
    private val proteusClientProvider: ProteusClientProvider,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : ClearClientDataUseCase {

    override suspend operator fun invoke() {
        withContext(dispatchers.io) {
            clearCrypto()
                .onSuccess {
                    kaliumLogger.e("Did not clear crypto storage")
                }
                .onFailure {
                    kaliumLogger.e("Error clearing crypto storage: $it")
                }
        }
    }

    private suspend fun clearCrypto(): Either<CoreFailure, Unit> =
        wrapProteusRequest {
            proteusClientProvider.clearLocalFiles()
        }.flatMap {
            wrapMLSRequest {
                mlsClientProvider.clearLocalFiles()
            }
        }
}
