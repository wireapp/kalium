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

import com.wire.kalium.common.functional.nullableFold
import com.wire.kalium.logic.data.app.AppMapper
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.service.ServiceDetails
import com.wire.kalium.logic.di.MapperProvider

/**
 * This use case is responsible for getting Service Details from given App ID.
 * @param appId QualifiedID of the App
 * @return ServiceDetails or NULL.
 */
public interface GetAppByIdUseCase {

    public suspend operator fun invoke(appId: QualifiedID): ServiceDetails?
}

internal class GetAppByIdUseCaseImpl internal constructor(
    private val appRepository: AppRepository,
    private val appMapper: AppMapper = MapperProvider.appMapper()
) : GetAppByIdUseCase {

    override suspend fun invoke(appId: QualifiedID): ServiceDetails? =
        appRepository.getAppById(appId = appId)
            .nullableFold({ null }, {
                it?.let { app -> appMapper.toServiceDetails(app) }
            })
}