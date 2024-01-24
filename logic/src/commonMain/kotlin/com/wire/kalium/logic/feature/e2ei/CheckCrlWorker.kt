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

import com.wire.kalium.logic.data.e2ei.CrlRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListUseCase
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Represents an interface for a CheckCrlWorker,
 * which is should keep the [CrlRepository] healthy.
 *
 * It will wait until the incremental sync is live and then check the CRLs if needed.
 */
internal interface CheckCrlWorker {
    suspend fun execute()
}

/**
 * Base implementation of [CheckCrlWorker].
 * @param crlRepository The CRL repository.
 * @param incrementalSyncRepository The incremental sync repository.
 * @param checkRevocationList The check revocation list use case.
 * @param minIntervalBetweenRefills The minimum interval between CRL checks.
 *
 */
internal class CheckCrlWorkerImpl(
    private val crlRepository: CrlRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val checkRevocationList: CheckRevocationListUseCase,
    private val minIntervalBetweenRefills: Duration = MIN_INTERVAL_BETWEEN_REFILLS
) : CheckCrlWorker {

    /**
     * Check the CRLs and update the expiration time if needed.
     */
    override suspend fun execute() {
        crlRepository.lastCrlCheckInstantFlow().collect { lastCheck ->
            val now = Clock.System.now()
            val nextCheckTime = lastCheck?.plus(minIntervalBetweenRefills) ?: now
            val delayUntilNextCheck = nextCheckTime - now
            delay(delayUntilNextCheck)
            waitUntilLiveAndCheckCRLs()
            crlRepository.setLastCRLCheckInstant(Clock.System.now())
        }
    }

    private suspend fun waitUntilLiveAndCheckCRLs() {
        incrementalSyncRepository.incrementalSyncState
            .filter { it is IncrementalSyncStatus.Live }
            .collect {
                crlRepository.getCRLs()?.cRLWithExpirationList?.forEach { crl ->
                    if (crl.expiration < Clock.System.now().epochSeconds.toULong()) {
                        checkRevocationList(crl.url).map { newExpirationTime ->
                            newExpirationTime?.let {
                                crlRepository.addOrUpdateCRL(crl.url, it)
                            }
                        }
                    }
                }
            }
    }

    private companion object {
        val MIN_INTERVAL_BETWEEN_REFILLS = 1.days
    }
}
