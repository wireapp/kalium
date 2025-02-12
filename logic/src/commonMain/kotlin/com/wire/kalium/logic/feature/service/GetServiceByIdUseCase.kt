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

import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.common.functional.nullableFold

/**
 * This use case is responsible for getting Service Details from given service ID.
 * @param serviceId contains service ID and Provider.
 * @return Service Details or NULL.
 */
interface GetServiceByIdUseCase {

    suspend operator fun invoke(
        serviceId: ServiceId
    ): ServiceDetails?
}

class GetServiceByIdUseCaseImpl internal constructor(
    private val serviceRepository: ServiceRepository
) : GetServiceByIdUseCase {

    override suspend fun invoke(serviceId: ServiceId): ServiceDetails? =
        serviceRepository.getServiceById(serviceId = serviceId)
            .nullableFold({ null }, { it })
}
