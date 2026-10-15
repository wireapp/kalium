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
package com.wire.kalium.logic.feature.app

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider

/** Synchronizes apps available to the current self-team. */
public interface SyncAppsUseCase {

    public suspend operator fun invoke(): Result

    public sealed interface Result {
        public data object Success : Result
        public data class Failure(val error: CoreFailure) : Result
    }
}

internal class SyncAppsUseCaseImpl internal constructor(
    private val appRepository: AppRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val apiVersion: Int
) : SyncAppsUseCase {

    override suspend fun invoke(): SyncAppsUseCase.Result =
        if (apiVersion < MIN_API_VERSION_TEAM_COLLABORATORS) {
            SyncAppsUseCase.Result.Success
        } else {
            selfTeamIdProvider()
                .flatMap { teamId ->
                    teamId?.let {
                        appRepository.syncApps(
                            teamId = it,
                            includeTeamApps = apiVersion >= MIN_API_VERSION_TEAM_APPS
                        )
                    } ?: Either.Right(Unit)
                }
                .fold(
                    { SyncAppsUseCase.Result.Failure(it) },
                    { SyncAppsUseCase.Result.Success }
                )
        }

    private companion object {
        const val MIN_API_VERSION_TEAM_COLLABORATORS = 10
        const val MIN_API_VERSION_TEAM_APPS = 15
    }
}
