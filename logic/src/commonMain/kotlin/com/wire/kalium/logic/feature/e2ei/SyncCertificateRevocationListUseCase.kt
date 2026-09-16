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
package com.wire.kalium.logic.feature.e2ei

import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import kotlinx.coroutines.flow.first

/** Wait until incremental sync is live, then check installed X.509 credentials once. */
internal interface SyncCertificateRevocationListUseCase {
    suspend operator fun invoke()
}

/**
 * Base implementation of [SyncCertificateRevocationListUseCase].
 * @param incrementalSyncRepository The incremental sync repository.
 */
internal class SyncCertificateRevocationListUseCaseImpl internal constructor(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val e2eiRepository: E2EIRepository,
    private val isE2EIEnabledUseCase: IsE2EIEnabledUseCase,
    kaliumLogger: KaliumLogger
) : SyncCertificateRevocationListUseCase {

    private val logger = kaliumLogger.withTextTag("CertificateRevocationListCheckWorker")

    override suspend operator fun invoke() {
        logger.d("Starting to monitor")
        incrementalSyncRepository.incrementalSyncState
            .first { it is IncrementalSyncStatus.Live }

        if (isE2EIEnabledUseCase()) {
            logger.i("Checking installed X.509 credentials")
            e2eiRepository.checkCredentials().onFailure { failure ->
                logger.w("Checking installed X.509 credentials failed: $failure")
            }
        }
    }
}
