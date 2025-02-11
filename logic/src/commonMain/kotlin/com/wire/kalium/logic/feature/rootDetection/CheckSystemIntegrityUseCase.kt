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

package com.wire.kalium.logic.feature.rootDetection

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger

/**
 * Checks if the system integrity by looking for signs that the
 * system on which kalium runs on has been given root access.
 *
 * If `wipeOnRootedDevice` is true  all account data will be deleted when
 * this check fails.
 */
interface CheckSystemIntegrityUseCase {

    sealed class Result {
        data object Success : Result()
        data object Failed : Result()
    }

    suspend fun invoke(): Result
}

internal class CheckSystemIntegrityUseCaseImpl(
    private val kaliumConfigs: KaliumConfigs,
    private val rootDetector: RootDetector,
    private val sessionRepository: SessionRepository,
) : CheckSystemIntegrityUseCase {

    override suspend fun invoke(): CheckSystemIntegrityUseCase.Result {
        return if (kaliumConfigs.wipeOnRootedDevice && rootDetector.isSystemRooted()) {
            kaliumLogger.w("System appears to have been rooted, deleting all account data...")
            deleteAllSessions()
                .onSuccess {
                    kaliumLogger.w("Successfully deleted all account data")
                }
                .onFailure {
                    kaliumLogger.w("Failure deleting account data: $it")
                }

            CheckSystemIntegrityUseCase.Result.Failed
        } else {
            CheckSystemIntegrityUseCase.Result.Success
        }
    }

    private suspend fun deleteAllSessions(): Either<StorageFailure, Boolean> =
        sessionRepository.allSessions()
            .flatMap {
                it.map {
                    sessionRepository.deleteSession(it.userId)
                }.foldToEitherWhileRight(true) { result, acc ->
                    result.map { acc }
                }
            }
}
