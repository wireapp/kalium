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
package com.wire.kalium.logic.feature.service

import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * This use case returns all services currently in the database.
 * In case it is empty, the repository will request the list from the API.
 *
 * @return Flow<List<ServiceDetails>>
 */
interface ObserveAllServicesUseCase {

    suspend operator fun invoke(): Flow<List<ServiceDetails>>
}

class ObserveAllServicesUseCaseImpl internal constructor(
    private val serviceRepository: ServiceRepository,
    private val teamRepository: TeamRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider
) : ObserveAllServicesUseCase {

    override suspend fun invoke(): Flow<List<ServiceDetails>> = flow {
        // TODO: This should be called only one time preferably by the view model to
        //  avoid unnecessary updates each time the use case is invoked
        //  or have a in memory timer to avoid calling it too often
        val scope = CoroutineScope(currentCoroutineContext())
        scope.launch {
            selfTeamIdProvider().getOrNull()?.let { teamId ->
                teamRepository.syncServices(teamId = teamId)
            }
        }

        emitAll(
            serviceRepository.observeAllServices().map { either ->
                either.fold(
                    { emptyList() },
                    { it }
                )
            }
        )
    }
}
