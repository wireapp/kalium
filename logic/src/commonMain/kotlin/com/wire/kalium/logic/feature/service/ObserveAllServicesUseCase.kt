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
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Emits the list of services currently persisted in the database.
 * Does not trigger a network sync — callers should invoke [SyncServicesUseCase] explicitly
 * when a remote refresh is desired.
 */
public interface ObserveAllServicesUseCase {

    public operator fun invoke(): Flow<List<ServiceDetails>>
}

internal class ObserveAllServicesUseCaseImpl internal constructor(
    private val serviceRepository: ServiceRepository
) : ObserveAllServicesUseCase {

    override fun invoke(): Flow<List<ServiceDetails>> =
        serviceRepository.observeAllServices().map { either ->
            either.fold(
                { emptyList() },
                { it }
            )
        }
}
