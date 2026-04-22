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

import com.wire.kalium.logic.data.app.AppRepository

public class AppScope internal constructor(
    private val appRepository: AppRepository
) {

    public val getAppById: GetAppByIdUseCase
        get() = GetAppByIdUseCaseImpl(
            appRepository = appRepository
        )

    public val observeIsAppMember: ObserveIsAppMemberUseCase
        get() = ObserveIsAppMemberUseCaseImpl(
            appRepository = appRepository
        )

    public val observeAllApps: ObserveAllAppsUseCase
        get() = ObserveAllAppsUseCaseImpl(
            appRepository = appRepository
        )

    public val searchAppsByName: SearchAppsByNameUseCase
        get() = SearchAppsByNameUseCaseImpl(
            appRepository = appRepository
        )
}
