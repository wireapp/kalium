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

/** Compatibility facade retaining only its own fresh extension and its constructor captures. */
public class AppScope internal constructor(private val api: LegacyAppApi) {
    public val syncApps: SyncAppsUseCase
        get() = api.appSyncApps

    public val getAppById: GetAppByIdUseCase
        get() = api.appGetAppById

    public val observeIsAppMember: ObserveIsAppMemberUseCase
        get() = api.appObserveIsAppMember

    public val observeAllApps: ObserveAllAppsUseCase
        get() = api.appObserveAllApps

    public val searchAppsByName: SearchAppsByNameUseCase
        get() = api.appSearchAppsByName
}
