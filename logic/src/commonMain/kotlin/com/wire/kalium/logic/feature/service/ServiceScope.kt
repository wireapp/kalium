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

import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider

class ServiceScope internal constructor(
    private val serviceRepository: ServiceRepository,
    private val teamRepository: TeamRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider
) {

    val getServiceById: GetServiceByIdUseCase
        get() = GetServiceByIdUseCaseImpl(
            serviceRepository = serviceRepository
        )

    val observeIsServiceMember: ObserveIsServiceMemberUseCase
        get() = ObserveIsServiceMemberUseCaseImpl(
            serviceRepository = serviceRepository
        )

    val observeAllServices: ObserveAllServicesUseCase
        get() = ObserveAllServicesUseCaseImpl(
            serviceRepository = serviceRepository,
            teamRepository = teamRepository,
            selfTeamIdProvider = selfTeamIdProvider
        )

    val searchServicesByName: SearchServicesByNameUseCase
        get() = SearchServicesByNameUseCaseImpl(
            serviceRepository = serviceRepository
        )
}
