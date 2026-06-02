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

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.app.AppMapper
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.di.MapperProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case returns all Apps currently in the database.
 *
 * @return Flow<List<ServiceDetails>>
 */
public interface ObserveAllAppsUseCase {

    public operator fun invoke(): Flow<List<ServiceDetails>>
}

internal class ObserveAllAppsUseCaseImpl internal constructor(
    private val appRepository: AppRepository,
    private val appMapper: AppMapper = MapperProvider.appMapper()
) : ObserveAllAppsUseCase {

    override fun invoke(): Flow<List<ServiceDetails>> =
        appRepository.observeAllApps().map {
            it.fold({ emptyList() }, { it })
        }.map { apps ->
            apps.map { app -> appMapper.toServiceDetails(app) }
        }
}
